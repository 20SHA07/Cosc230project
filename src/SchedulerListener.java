// Lets the allowance part (Person 2) react when a job finishes or is cancelled,
// so the scheduler doesn't have to know about charging time. Can be left unset.
public interface SchedulerListener {

    void onJobCompleted(Job job, int runtimeMinutes);

    void onRunningCancelled(Job job, int runtimeMinutes);

    void onQueuedCancelled(Job job);
}
