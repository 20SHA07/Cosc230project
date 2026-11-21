import java.time.LocalDateTime;
import java.util.ArrayList;

// Runs the simulation: four GPUs, one FCFS queue and the shared clock.
// Jobs are the ones from Person 2 (Job); this class starts, completes and
// cancels them through their own startJob/completeJob/cancelJob methods.
public class Scheduler {

    public static final int GPU_COUNT = 4;

    // Job status values (same text Person 2's Job uses).
    public static final String QUEUED = "Queued";
    public static final String RUNNING = "Running";
    public static final String COMPLETED = "Completed";
    public static final String CANCELLED = "Cancelled";

    private SimulationClock clock;
    private GPU[] gpus;
    private ArrayList<Job> queue = new ArrayList<Job>();     // waiting jobs
    private ArrayList<Job> history = new ArrayList<Job>();   // every job submitted

    private SchedulerListener listener;    // optional (allowance/storage)

    private int totalBusyGpuMinutes = 0;   // for utilisation

    public Scheduler(SimulationClock clock) {
        this.clock = clock;
        gpus = new GPU[GPU_COUNT];
        for (int i = 0; i < GPU_COUNT; i++) {
            gpus[i] = new GPU(i + 1);      // GPU ids 1..4
        }
    }

    public void setListener(SchedulerListener listener) {
        this.listener = listener;
    }

    public SimulationClock getClock() { return clock; }
    public GPU[] getGpus() { return gpus; }
    public GPU getGpu(int id) { return gpus[id - 1]; }
    public ArrayList<Job> getQueue() { return new ArrayList<Job>(queue); }
    public ArrayList<Job> getHistory() { return new ArrayList<Job>(history); }

    // Add an accepted job to the queue and start it if a GPU is free.
    public void submit(Job job) {
        queue.add(job);
        history.add(job);
        sortQueue();
        dispatch();
    }

    // Move the clock one minute: finish jobs that are done, then start waiting ones.
    public void advanceMinute() {
        if (!clock.isInitialised()) {
            throw new IllegalStateException("Initialise the clock before advancing.");
        }

        // the GPUs busy this minute count towards utilisation
        for (int i = 0; i < GPU_COUNT; i++) {
            if (gpus[i].isBusy()) {
                totalBusyGpuMinutes++;
            }
        }

        clock.advance();

        // add a minute to each running job, complete the finished ones
        for (int i = 0; i < GPU_COUNT; i++) {
            GPU gpu = gpus[i];
            if (gpu.isBusy()) {
                gpu.tick();
                if (gpu.isFinished()) {
                    int runtime = gpu.getCurrentJob().getEstimatedDuration();
                    Job done = gpu.complete();
                    done.completeJob(clock.formattedNow());
                    if (listener != null) {
                        listener.onJobCompleted(done, runtime);
                    }
                }
            }
        }

        dispatch();
    }

    // Fill free GPUs from the front of the queue, lowest GPU id first.
    private void dispatch() {
        if (queue.isEmpty()) return;
        sortQueue();
        for (int i = 0; i < GPU_COUNT; i++) {
            if (queue.isEmpty()) break;
            if (gpus[i].isFree()) {
                Job next = queue.remove(0);
                gpus[i].start(next, clock.now());
                next.startJob(gpus[i].getId(), clock.formattedNow());
            }
        }
    }

    // Selection sort by job id. Ids are given in submission order, so this is FCFS.
    private void sortQueue() {
        for (int i = 0; i < queue.size() - 1; i++) {
            int best = i;
            for (int j = i + 1; j < queue.size(); j++) {
                if (queue.get(j).getJobId() < queue.get(best).getJobId()) {
                    best = j;
                }
            }
            if (best != i) {
                Job tmp = queue.get(i);
                queue.set(i, queue.get(best));
                queue.set(best, tmp);
            }
        }
    }

    // Cancel a job still in the queue (no charge).
    public boolean cancelQueued(Job job, String by, String reason) {
        if (!queue.remove(job)) {
            return false;
        }
        job.cancelJob(clock.formattedNow(), by, reason);
        if (listener != null) {
            listener.onQueuedCancelled(job);
        }
        return true;
    }

    // Cancel a running job: free the GPU, charge the time used, start the next job.
    public boolean cancelRunning(Job job, String by, String reason) {
        GPU gpu = runningGpuOf(job);
        if (gpu == null) {
            return false;
        }
        int runtime = gpu.getMinutesRun();
        gpu.release();
        job.cancelJob(clock.formattedNow(), by, reason);
        if (listener != null) {
            listener.onRunningCancelled(job, runtime);
        }
        dispatch();
        return true;
    }

    private GPU runningGpuOf(Job job) {
        for (int i = 0; i < GPU_COUNT; i++) {
            if (gpus[i].isBusy() && gpus[i].getCurrentJob() == job) {
                return gpus[i];
            }
        }
        return null;
    }

    // Work out when each waiting job would start/finish, without touching the
    // real GPUs. freeIn = minutes until each GPU is free (a copy we can change).
    public ArrayList<QueuePrediction> predictQueue() {
        ArrayList<QueuePrediction> result = new ArrayList<QueuePrediction>();
        if (!clock.isInitialised()) return result;

        LocalDateTime now = clock.now();

        int[] freeIn = new int[GPU_COUNT];
        for (int i = 0; i < GPU_COUNT; i++) {
            freeIn[i] = gpus[i].isFree() ? 0 : gpus[i].remainingRuntime();
        }

        ArrayList<Job> ordered = new ArrayList<Job>(queue);
        sortById(ordered);

        int position = 1;
        for (int k = 0; k < ordered.size(); k++) {
            Job job = ordered.get(k);

            // pick the GPU that frees up soonest (ties -> lowest id)
            int pick = 0;
            for (int i = 1; i < GPU_COUNT; i++) {
                if (freeIn[i] < freeIn[pick]) {
                    pick = i;
                }
            }

            int startOffset = freeIn[pick];
            int finishOffset = startOffset + job.getEstimatedDuration();
            LocalDateTime start = now.plusMinutes(startOffset);
            LocalDateTime finish = now.plusMinutes(finishOffset);

            result.add(new QueuePrediction(
                    job, position, gpus[pick].getId(), start, finish, startOffset));

            freeIn[pick] = finishOffset;
            position++;
        }
        return result;
    }

    private void sortById(ArrayList<Job> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            int best = i;
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(j).getJobId() < list.get(best).getJobId()) {
                    best = j;
                }
            }
            if (best != i) {
                Job tmp = list.get(i);
                list.set(i, list.get(best));
                list.set(best, tmp);
            }
        }
    }

    public int getTotalBusyGpuMinutes() { return totalBusyGpuMinutes; }

    public int busyGpuCount() {
        int n = 0;
        for (int i = 0; i < GPU_COUNT; i++) {
            if (gpus[i].isBusy()) n++;
        }
        return n;
    }

    // busy GPU-minutes / (4 * elapsed minutes) * 100; -1 means N/A at the start.
    public double utilisationPercent() {
        int elapsed = clock.getElapsedMinutes();
        if (elapsed <= 0) return -1;
        return (totalBusyGpuMinutes * 100.0) / (GPU_COUNT * elapsed);
    }

    public String utilisationText() {
        double u = utilisationPercent();
        if (u < 0) return "N/A";
        return Math.round(u) + "%";
    }

    public int countByStatus(String status) {
        int n = 0;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getStatus().equals(status)) n++;
        }
        return n;
    }

    // --- used by Person 4 when restoring a saved simulation ---

    public void restoreBusyMinutes(int totalBusyGpuMinutes) {
        this.totalBusyGpuMinutes = totalBusyGpuMinutes;
    }

    public void registerRestoredJob(Job job, int minutesRun) {
        history.add(job);
        if (job.getStatus().equals(QUEUED)) {
            queue.add(job);
            sortQueue();
        } else if (job.getStatus().equals(RUNNING) && job.getAssignedGpuId() >= 1) {
            LocalDateTime startedAt = clock.now().minusMinutes(minutesRun);
            gpus[job.getAssignedGpuId() - 1].restore(job, minutesRun, startedAt);
        }
    }
}
