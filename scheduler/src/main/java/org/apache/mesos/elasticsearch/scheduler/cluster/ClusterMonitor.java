package org.apache.mesos.elasticsearch.scheduler.cluster;

import org.apache.log4j.Logger;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.elasticsearch.scheduler.Configuration;
import org.apache.mesos.elasticsearch.scheduler.healthcheck.BumpExecutor;
import org.apache.mesos.elasticsearch.scheduler.healthcheck.ExecutorHealth;
import org.apache.mesos.elasticsearch.scheduler.healthcheck.ExecutorHealthCheck;
import org.apache.mesos.elasticsearch.scheduler.healthcheck.PollService;
import org.apache.mesos.elasticsearch.scheduler.state.ClusterState;
import org.apache.mesos.elasticsearch.scheduler.state.ESTaskStatus;

/**
 * Contains all cluster information. Monitors state of cluster elements.
 */
public class ClusterMonitor {
    private static final Logger LOGGER = Logger.getLogger(ClusterMonitor.class);
    private final Configuration configuration;
    private final SchedulerDriver driver;
    private final ClusterState clusterState;

    public ClusterMonitor(Configuration configuration, SchedulerDriver driver, ClusterState clusterState) {
        this.configuration = configuration;
        this.driver = driver;
        this.clusterState = clusterState;
    }

    /**
     * Monitor a new, or existing task.
     * @param task The task to monitor
     * @param callback The Scheduler to call with status updates.
     */
    public void monitorTask(Protos.TaskInfo task, Scheduler callback) {
        ESTaskStatus taskStatus = addNewTaskToCluster(task);
        createNewExecutorBump(task);
        createNewExecutorHealthMonitor(callback, taskStatus);
    }

    public ClusterState getClusterState() {
        return clusterState;
    }

    private void createNewExecutorHealthMonitor(Scheduler scheduler, ESTaskStatus taskStatus) {
        ExecutorHealth health = new ExecutorHealth(scheduler, driver, taskStatus, 10000L);
        new ExecutorHealthCheck(new PollService(health, 5000L));
    }

    private void createNewExecutorBump(Protos.TaskInfo taskInfo) {
        BumpExecutor bumpExecutor = new BumpExecutor(driver, taskInfo);
        new ExecutorHealthCheck(new PollService(bumpExecutor, 5000L));
    }


    private ESTaskStatus addNewTaskToCluster(Protos.TaskInfo taskInfo) {
        ESTaskStatus taskStatus = new ESTaskStatus(configuration.getState(), configuration.getFrameworkId(), taskInfo);
        taskStatus.setStatus(taskStatus.getDefaultStatus()); // This is a new task, so set default state until we get an update
        clusterState.addTask(taskInfo);
        return taskStatus;
    }

    public void updateTask(Protos.TaskStatus status) {
        try {
            // Update cluster state, if necessary
            if (getClusterState().exists(status.getTaskId())) {
                ESTaskStatus executorState = getClusterState().getStatus(status.getTaskId());
                // Update state of Executor
                executorState.setStatus(status);
            } else {
                LOGGER.warn("Could not find task in cluster state.");
            }
        } catch (IllegalStateException e) {
            LOGGER.error("Unable to write executor state to zookeeper", e);
        }
    }
}
