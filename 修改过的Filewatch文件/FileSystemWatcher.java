/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.devtools.filewatch;

import com.ruoyi.RuoYiApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.*;
import java.net.Socket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Watches specific directories for file changes.
 *
 * @author Andy Clement
 * @author Phillip Webb
 * @see FileChangeListener
 * @since 1.3.0
 *
 * 这个根据idea 修改文件会触发两次变动来重写，让idea重编译彻底完成后在触发更新。并且尽可能衔接的快一点
 *
 * 获取项目启动端口号，然后把项目名称和端口号发送给idea插件，idea插件检测每次接收到请求后，如果检测到项目变动，自动编译，并通知项目重启，
 * 项目重启后，再把请求发送过去。
 *
 */
public class FileSystemWatcher {


    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(1000);

    private static final Duration DEFAULT_QUIET_PERIOD = Duration.ofMillis(400);

    private final List<FileChangeListener> listeners = new ArrayList<>();

    private final boolean daemon;

    private final long pollInterval;

    private final long quietPeriod;

    private final SnapshotStateRepository snapshotStateRepository;

    private final AtomicInteger remainingScans = new AtomicInteger(-1);

    private final Map<File, DirectorySnapshot> directories = new HashMap<>();

    private Thread watchThread;

    private FileFilter triggerFilter;

    private final Object monitor = new Object();

    /**
     * Create a new {@link FileSystemWatcher} instance.
     */
    public FileSystemWatcher() {
        this(true, DEFAULT_POLL_INTERVAL, DEFAULT_QUIET_PERIOD);
    }

    /**
     * Create a new {@link FileSystemWatcher} instance.
     *
     * @param daemon       if a daemon thread used to monitor changes
     * @param pollInterval the amount of time to wait between checking for changes
     * @param quietPeriod  the amount of time required after a change has been detected to
     *                     ensure that updates have completed
     */
    public FileSystemWatcher(boolean daemon, Duration pollInterval, Duration quietPeriod) {
        this(daemon, pollInterval, quietPeriod, null);
    }

    /**
     * Create a new {@link FileSystemWatcher} instance.
     *
     * @param daemon                  if a daemon thread used to monitor changes
     * @param pollInterval            the amount of time to wait between checking for changes
     * @param quietPeriod             the amount of time required after a change has been detected to
     *                                ensure that updates have completed
     * @param snapshotStateRepository the snapshot state repository
     * @since 2.4.0
     */
    public FileSystemWatcher(boolean daemon, Duration pollInterval, Duration quietPeriod,
                             SnapshotStateRepository snapshotStateRepository) {
        Assert.notNull(pollInterval, "PollInterval must not be null");
        Assert.notNull(quietPeriod, "QuietPeriod must not be null");
        Assert.isTrue(pollInterval.toMillis() > 0, "PollInterval must be positive");
        Assert.isTrue(quietPeriod.toMillis() > 0, "QuietPeriod must be positive");
        Assert.isTrue(pollInterval.toMillis() > quietPeriod.toMillis(),
                "PollInterval must be greater than QuietPeriod");
        this.daemon = daemon;
        this.pollInterval = pollInterval.toMillis();
        this.quietPeriod = quietPeriod.toMillis();
        this.snapshotStateRepository = (snapshotStateRepository != null) ? snapshotStateRepository
                : SnapshotStateRepository.NONE;
    }

    /**
     * Add listener for file change events. Cannot be called after the watcher has been
     * {@link #start() started}.
     *
     * @param fileChangeListener the listener to add
     */
    public void addListener(FileChangeListener fileChangeListener) {
        Assert.notNull(fileChangeListener, "FileChangeListener must not be null");
        synchronized (this.monitor) {
            checkNotStarted();
            this.listeners.add(fileChangeListener);
        }
    }

    /**
     * Add source directories to monitor. Cannot be called after the watcher has been
     * {@link #start() started}.
     *
     * @param directories the directories to monitor
     */
    public void addSourceDirectories(Iterable<File> directories) {
        Assert.notNull(directories, "Directories must not be null");
        synchronized (this.monitor) {
            directories.forEach(this::addSourceDirectory);
        }
    }

    /**
     * Add a source directory to monitor. Cannot be called after the watcher has been
     * {@link #start() started}.
     *
     * @param directory the directory to monitor
     */
    public void addSourceDirectory(File directory) {
        Assert.notNull(directory, "Directory must not be null");
        Assert.isTrue(!directory.isFile(), () -> "Directory '" + directory + "' must not be a file");
        synchronized (this.monitor) {
            checkNotStarted();
            this.directories.put(directory, null);
        }
    }

    /**
     * Set an optional {@link FileFilter} used to limit the files that trigger a change.
     *
     * @param triggerFilter a trigger filter or null
     */
    public void setTriggerFilter(FileFilter triggerFilter) {
        synchronized (this.monitor) {
            this.triggerFilter = triggerFilter;
        }
    }

    private void checkNotStarted() {
        Assert.state(this.watchThread == null, "FileSystemWatcher already started");
    }

    /**
     * Start monitoring the source directory for changes.
     */
    public void start() {
        synchronized (this.monitor) {
            createOrRestoreInitialSnapshots();
            if (this.watchThread == null) {
                Map<File, DirectorySnapshot> localDirectories = new HashMap<>(this.directories);
                Watcher watcher = new Watcher(this.remainingScans, new ArrayList<>(this.listeners), this.triggerFilter,
                        this.pollInterval, this.quietPeriod, localDirectories, this.snapshotStateRepository);
                this.watchThread = new Thread(watcher);
                this.watchThread.setName("File Watcher");
                this.watchThread.setDaemon(this.daemon);
                this.watchThread.start();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void createOrRestoreInitialSnapshots() {
        Map<File, DirectorySnapshot> restored = (Map<File, DirectorySnapshot>) this.snapshotStateRepository.restore();
        this.directories.replaceAll((f, v) -> {
            DirectorySnapshot restoredSnapshot = (restored != null) ? restored.get(f) : null;
            return (restoredSnapshot != null) ? restoredSnapshot : new DirectorySnapshot(f);
        });
    }

    /**
     * Stop monitoring the source directories.
     */
    public void stop() {
        stopAfter(0);
    }

    /**
     * Stop monitoring the source directories.
     *
     * @param remainingScans the number of remaining scans
     */
    void stopAfter(int remainingScans) {
        Thread thread;
        synchronized (this.monitor) {
            thread = this.watchThread;
            if (thread != null) {
                this.remainingScans.set(remainingScans);
                if (remainingScans <= 0) {
                    thread.interrupt();
                }
            }
            this.watchThread = null;
        }
        if (thread != null && Thread.currentThread() != thread) {
            try {
                thread.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class Watcher implements Runnable {
        Logger log = LoggerFactory.getLogger(RuoYiApplication.class);
        private final AtomicInteger remainingScans;

        private final List<FileChangeListener> listeners;

        private final FileFilter triggerFilter;

        private final long pollInterval;

        private final long quietPeriod;

        private Map<File, DirectorySnapshot> directories;

        private final SnapshotStateRepository snapshotStateRepository;

        private Watcher(AtomicInteger remainingScans, List<FileChangeListener> listeners, FileFilter triggerFilter,
                        long pollInterval, long quietPeriod, Map<File, DirectorySnapshot> directories,
                        SnapshotStateRepository snapshotStateRepository) {
            this.remainingScans = remainingScans;
            this.listeners = listeners;
            this.triggerFilter = triggerFilter;
            this.pollInterval = pollInterval;
            this.quietPeriod = quietPeriod;
            this.directories = directories;
            this.snapshotStateRepository = snapshotStateRepository;

        }

//		@Override
//		public void run() {
//			//remainingScans的值只有两种 -1和0，传0是为了停止循环
//			int remainingScans = this.remainingScans.get();
//			while (remainingScans > 0 || remainingScans == -1) {
//				try {
//					if (remainingScans > 0) {
//						this.remainingScans.decrementAndGet();
//					}
//					scan();
//				}
//				catch (InterruptedException ex) {
//					Thread.currentThread().interrupt();
//				}
//				remainingScans = this.remainingScans.get();
//			}
//		}

        private void checkRead(InputStream in) throws IOException, InterruptedException {
          //  in.read(new byte[1]);
            while (!Thread.currentThread().isInterrupted()&&!isStop()){
                if (in.available()>0){
                    in.read(new byte[1]);
                    break;
                }else {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        throw e;
                    }
                }
            }
        }

        @Override
        public void run() {
            //remainingScans的值只有两种 -1和0，传0是为了停止循环，并且生命周期是一次性的也就是触发变更后就会销毁
            Map<File, DirectorySnapshot> previous = this.directories;
            //todo 基于idea的情况，基本一次编译再1.3s 到 2s 之间，对于修改文件，会触发两次文件变动

            this.directories.forEach((x,y)->{
                System.out.println(x.toString());
            });


            try {
                log.info("监听开启，建立连接");
                Socket socket = new Socket("127.0.0.1",60012);

                log.info("监听开启，建立连接成功");
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                writer.write("compile-listener");//todo idea创建项目时候的名称，也就是比如这个插件的项目名称就是compile-listener
                writer.newLine();
                writer.flush();

                InputStream in = socket.getInputStream();

                //read可以被中断，stopAfter方法里中断线程了
                while (!isStop()){

                    try {
                        checkRead(in);
                        log.info("接收到编译结束事件");
                        Map<File, DirectorySnapshot> current = getCurrentSnapshots();
                        if (isDifferent(this.directories, current)) {
                            socket.close();
                           // Thread.currentThread().isInterrupted();
                          //  Thread.sleep(3000);
                            updateSnapshots(current.values());
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }


                }

                socket.close();
            }  catch (IOException e) {
                log.info("监听失败");
                e.printStackTrace();
            }


            //第一个循环，检测文件变动
//            while (!isStop()) {
//                try {
//                    if (this.remainingScans.get() > 0) {
//                        this.remainingScans.decrementAndGet();
//                    }
//                    //首先是等待变更发生
//                    Map<File, DirectorySnapshot> current = getCurrentSnapshots();
//                    boolean isDifferent = isDifferent(previous, current);
//                    previous = current;
//                    //如果是第一阶段，检测变更
//                    if (isDifferent) {
//                        //文件变化了
//                        log.info("检测到变动");
//                        break;
//                    }
//                    Thread.sleep(300);
//
//                } catch (InterruptedException ex) {
//                    Thread.currentThread().interrupt();
//                }
//            }
//
//            int compareCount = 0;
//            int maxChangeCount = 2; //最大变更次数
//            int changeCount = 1;
//            //第二个循环，等待变动结束
//            while (!isStop()) {
//                try {
//                    log.info("等待变动结束");
//
//                    Map<File, DirectorySnapshot> current = getCurrentSnapshots();
//                    boolean isDifferent = isDifferent(previous, current);
//
//                    //等待前后两次文件没有变化
//                    if (!isDifferent) {
//                        compareCount++; //保持不变次数加1
//                    } else {
//                        changeCount++;
//                    }
//
//                    if (changeCount >= maxChangeCount || compareCount > 5) {
//                        log.info("变动结束");
//                        updateSnapshots(current.values());
//                        break;
//                    }
//
//                    Thread.sleep(300);
//                    previous = current;
//
//                } catch (InterruptedException ex) {
//                    Thread.currentThread().interrupt();
//                }
//            }
//
            log.info("监听结束");
        }


        private boolean isStop() {
            int remainingScans = this.remainingScans.get();
            if (remainingScans == 0) {
                return true;
            }
            return false;
        }


        /**
         * 监听规则，如果没有遇到文件变更，就固定100毫秒扫描一次，如果发现变更，就规定睡眠600毫秒，如果下次再扫描发现没有变更
         * 就认为变更完成，开始触发
         */
        private void scan() throws InterruptedException {
            Thread.sleep(this.pollInterval - this.quietPeriod);
            Map<File, DirectorySnapshot> previous;
            Map<File, DirectorySnapshot> current = this.directories;
            boolean isDifferent;
            do {
                previous = current;
                current = getCurrentSnapshots();
                Thread.sleep(this.quietPeriod);
                isDifferent = isDifferent(previous, current);
            }
            while (isDifferent);
            if (isDifferent(this.directories, current)) {
                updateSnapshots(current.values());
            }
        }

        private boolean isDifferent(Map<File, DirectorySnapshot> previous, Map<File, DirectorySnapshot> current) {
            if (!previous.keySet().equals(current.keySet())) {
                return true;
            }
            for (Map.Entry<File, DirectorySnapshot> entry : previous.entrySet()) {
                DirectorySnapshot previousDirectory = entry.getValue();
                DirectorySnapshot currentDirectory = current.get(entry.getKey());
                if (!previousDirectory.equals(currentDirectory, this.triggerFilter)) {
                    return true;
                }
            }
            return false;
        }

        private Map<File, DirectorySnapshot> getCurrentSnapshots() {
            Map<File, DirectorySnapshot> snapshots = new LinkedHashMap<>();
            for (File directory : this.directories.keySet()) {
                snapshots.put(directory, new DirectorySnapshot(directory));
            }
            return snapshots;
        }

        private void updateSnapshots(Collection<DirectorySnapshot> snapshots) {
            Map<File, DirectorySnapshot> updated = new LinkedHashMap<>();
            Set<ChangedFiles> changeSet = new LinkedHashSet<>();
            for (DirectorySnapshot snapshot : snapshots) {
                DirectorySnapshot previous = this.directories.get(snapshot.getDirectory());
                updated.put(snapshot.getDirectory(), snapshot);
                ChangedFiles changedFiles = previous.getChangedFiles(snapshot, this.triggerFilter);
                if (!changedFiles.getFiles().isEmpty()) {
                    changeSet.add(changedFiles);
                }
            }
            this.directories = updated;
            this.snapshotStateRepository.save(updated);
            if (!changeSet.isEmpty()) {
                fireListeners(Collections.unmodifiableSet(changeSet));
            }
        }

        private void fireListeners(Set<ChangedFiles> changeSet) {
            for (FileChangeListener listener : this.listeners) {
                listener.onChange(changeSet);
            }
        }

    }

}
