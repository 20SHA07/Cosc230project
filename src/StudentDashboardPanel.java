import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public class StudentDashboardPanel extends JPanel implements ActionListener {

    private String studentId;
    private String simulationTime;

    private JobManager manager;

    // Allowance labels
    private JLabel allocatedLabel;
    private JLabel usedLabel;
    private JLabel reservedLabel;
    private JLabel availableLabel;

    // Job input components
    private JTextField titleField;
    private JTextField applicationField;
    private JTextArea descriptionArea;
    private JComboBox<String> taskSizeBox;
    private JLabel durationLabel;

    // Buttons
    private JButton submitButton;
    private JButton cancelButton;
    private JButton detailsButton;
    private JButton searchButton;
    private JButton showAllButton;

    // Search component
    private JTextField searchField;

    // Jobs table
    private JTable jobsTable;
    private DefaultTableModel tableModel;

    public StudentDashboardPanel(String studentId, JobManager manager) {

        this.studentId = studentId;
        this.manager = manager;
        simulationTime = null;

        setLayout(new BorderLayout(10, 10));

        createAllowancePanel();
        createJobFormPanel();
        createJobsTable();
        createButtonPanel();

        refreshAllowance();
        refreshJobs(manager.getStudentJobs(studentId));
    }

    private void createAllowancePanel() {

        JPanel allowancePanel = new JPanel(new GridLayout(1, 4, 10, 10));

        allocatedLabel = new JLabel("Allocated: 0");
        usedLabel = new JLabel("Used: 0");
        reservedLabel = new JLabel("Reserved: 0");
        availableLabel = new JLabel("Available: 0");

        allowancePanel.add(allocatedLabel);
        allowancePanel.add(usedLabel);
        allowancePanel.add(reservedLabel);
        allowancePanel.add(availableLabel);

        add(allowancePanel, BorderLayout.NORTH);
    }

    private void createJobFormPanel() {

        JPanel formPanel = new JPanel(new GridLayout(5, 2, 10, 10));

        titleField = new JTextField();
        applicationField = new JTextField();
        descriptionArea = new JTextArea(3, 20);

        String[] sizes = {"Small", "Medium", "Large", "Very large"};

        taskSizeBox = new JComboBox<String>(sizes);
        taskSizeBox.addActionListener(this);

        durationLabel = new JLabel("5 minutes");

        formPanel.add(new JLabel("Job title:"));
        formPanel.add(titleField);

        formPanel.add(new JLabel("Application name:"));
        formPanel.add(applicationField);

        formPanel.add(new JLabel("Description:"));
        formPanel.add(new JScrollPane(descriptionArea));

        formPanel.add(new JLabel("Task size:"));
        formPanel.add(taskSizeBox);

        formPanel.add(new JLabel("Duration:"));
        formPanel.add(durationLabel);

        add(formPanel, BorderLayout.WEST);
    }

    private void createJobsTable() {

        String[] columns = {
                "Job ID",
                "Title",
                "Application",
                "Size",
                "Duration",
                "Status",
                "GPU"
        };

        tableModel = new DefaultTableModel(columns, 0);

        jobsTable = new JTable(tableModel);

        add(new JScrollPane(jobsTable), BorderLayout.CENTER);
    }

    private void createButtonPanel() {

        JPanel buttonPanel = new JPanel();

        submitButton = new JButton("Submit Job");
        cancelButton = new JButton("Cancel Job");
        detailsButton = new JButton("View Details");

        searchField = new JTextField(12);

        searchButton = new JButton("Search");
        showAllButton = new JButton("Show All");

        submitButton.addActionListener(this);
        cancelButton.addActionListener(this);
        detailsButton.addActionListener(this);
        searchButton.addActionListener(this);
        showAllButton.addActionListener(this);

        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(detailsButton);
        buttonPanel.add(new JLabel("Search:"));
        buttonPanel.add(searchField);
        buttonPanel.add(searchButton);
        buttonPanel.add(showAllButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == taskSizeBox) {
            updateDuration();
        }
        else if (e.getSource() == submitButton) {
            submitJob();
        }
        else if (e.getSource() == cancelButton) {
            cancelSelectedJob();
        }
        else if (e.getSource() == detailsButton) {
            showSelectedJobDetails();
        }
        else if (e.getSource() == searchButton) {
            searchJobs();
        }
        else if (e.getSource() == showAllButton) {
            refreshJobs(manager.getStudentJobs(studentId));
        }
    }

    private void updateDuration() {

        String size = (String) taskSizeBox.getSelectedItem();

        if (size.equals("Small")) {
            durationLabel.setText("5 minutes");
        }
        else if (size.equals("Medium")) {
            durationLabel.setText("15 minutes");
        }
        else if (size.equals("Large")) {
            durationLabel.setText("30 minutes");
        }
        else {
            durationLabel.setText("60 minutes");
        }
    }

    private void submitJob() {

        if (simulationTime == null) {
            JOptionPane.showMessageDialog(this, "The simulation clock has not been initialized.");
            return;
        }

        String title = titleField.getText();
        String application = applicationField.getText();
        String description = descriptionArea.getText();
        String taskSize = (String) taskSizeBox.getSelectedItem();

        Job job = manager.submitJob(studentId, title, application, description, taskSize, simulationTime);

        JOptionPane.showMessageDialog(this, manager.getLastMessage());

        if (job != null) {
            clearForm();
            refreshAllowance();
            refreshJobs(manager.getStudentJobs(studentId));
        }
    }

    private void cancelSelectedJob() {

        int selectedRow = jobsTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a job from the table.");
            return;
        }

        if (simulationTime == null) {
            JOptionPane.showMessageDialog(this, "The simulation clock has not been initialized.");
            return;
        }

        int jobId = (int) tableModel.getValueAt(selectedRow, 0);

        Job job = manager.findJob(jobId);

        if (job == null) {
            JOptionPane.showMessageDialog(this, "Job not found.");
            return;
        }

        String reason = JOptionPane.showInputDialog(this, "Enter the cancellation reason:");

        if (reason == null || reason.trim().equals("")) {
            JOptionPane.showMessageDialog(this, "A cancellation reason is required.");
            return;
        }

        int elapsedRuntime = 0;

        if (job.getStatus().equals("Running")) {
            elapsedRuntime = calculateElapsedRuntime(job);

            if (elapsedRuntime == -1) {
                JOptionPane.showMessageDialog(this, "The elapsed runtime could not be calculated.");
                return;
            }
        }

        boolean cancelled = manager.cancelJob(jobId, studentId, reason, simulationTime, elapsedRuntime);

        JOptionPane.showMessageDialog(this, manager.getLastMessage());

        if (cancelled) {
            refreshAllowance();
            refreshJobs(manager.getStudentJobs(studentId));
        }
    }

    private void showSelectedJobDetails() {

        int selectedRow = jobsTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a job from the table.");
            return;
        }

        int jobId = (int) tableModel.getValueAt(selectedRow, 0);

        Job job = manager.findJob(jobId);

        if (job == null) {
            JOptionPane.showMessageDialog(this, "Job not found.");
            return;
        }

        String gpu;

        if (job.getAssignedGpuId() == -1) {
            gpu = "Not assigned";
        }
        else {
            gpu = Integer.toString(job.getAssignedGpuId());
        }

        String startTime;

        if (job.getStartTime() == null) {
            startTime = "-";
        }
        else {
            startTime = job.getStartTime();
        }

        String finishTime;

        if (job.getFinishTime() == null) {
            finishTime = "-";
        }
        else {
            finishTime = job.getFinishTime();
        }

        String cancellationReason;

        if (job.getCancellationReason() == null) {
            cancellationReason = "-";
        }
        else {
            cancellationReason = job.getCancellationReason();
        }

        String details = "Job ID: " + job.getJobId()
                + "\nStudent ID: " + job.getStudentId()
                + "\nTitle: " + job.getTitle()
                + "\nApplication: " + job.getApplicationName()
                + "\nDescription: " + job.getDescription()
                + "\nTask size: " + job.getTaskSize()
                + "\nDuration: " + job.getEstimatedDuration() + " minutes"
                + "\nSubmission time: " + job.getSubmissionTime()
                + "\nStatus: " + job.getStatus()
                + "\nGPU: " + gpu
                + "\nStart time: " + startTime
                + "\nFinish time: " + finishTime
                + "\nCharged minutes: " + job.getChargedMinutes()
                + "\nCancellation reason: " + cancellationReason;

        JOptionPane.showMessageDialog(this, details, "Job Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private int calculateElapsedRuntime(Job job) {

        try {
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");

            Date start = format.parse(job.getStartTime());
            Date current = format.parse(simulationTime);

            long difference = current.getTime() - start.getTime();

            int minutes = (int) (difference / 60000);

            if (minutes < 0) {
                return -1;
            }

            if (minutes > job.getEstimatedDuration()) {
                minutes = job.getEstimatedDuration();
            }

            return minutes;
        }
        catch (Exception e) {
            return -1;
        }
    }

    private void searchJobs() {

        String keyword = searchField.getText();

        if (keyword.trim().equals("")) {
            refreshJobs(manager.getStudentJobs(studentId));
            return;
        }

        ArrayList<Job> results = manager.searchStudentJobs(studentId, keyword);

        refreshJobs(results);
    }

    private void clearForm() {

        titleField.setText("");
        applicationField.setText("");
        descriptionArea.setText("");
        taskSizeBox.setSelectedIndex(0);
    }

    public void refreshAllowance() {

        TimeAllowance allowance = manager.findAllowance(studentId);

        if (allowance != null) {
            allocatedLabel.setText("Allocated: " + allowance.getAllocatedMinutes());
            usedLabel.setText("Used: " + allowance.getUsedMinutes());
            reservedLabel.setText("Reserved: " + allowance.getReservedMinutes());
            availableLabel.setText("Available: " + allowance.getAvailableMinutes());
        }
    }

    public void refreshJobs(ArrayList<Job> jobs) {

        tableModel.setRowCount(0);

        for (Job job : jobs) {

            Object gpu;

            if (job.getAssignedGpuId() == -1) {
                gpu = "Not assigned";
            }
            else {
                gpu = job.getAssignedGpuId();
            }

            Object[] row = {
                    job.getJobId(),
                    job.getTitle(),
                    job.getApplicationName(),
                    job.getTaskSize(),
                    job.getEstimatedDuration(),
                    job.getStatus(),
                    gpu
            };

            tableModel.addRow(row);
        }
    }

    public void setSimulationTime(String simulationTime) {
        this.simulationTime = simulationTime;
    }
}