import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class RequestSaverTab {
    private final MontoyaApi api;
    private final SessionManager sessionManager;
    private final JPanel mainPanel;
    private final JTable requestTable;
    private final RequestTableModel tableModel;
    private JTextField outputDirField;
    private JLabel statusLabel;
    private HttpRequestEditor requestViewer;
    private HttpResponseEditor responseViewer;

    public RequestSaverTab(MontoyaApi api, SessionManager sessionManager) {
        this.api = api;
        this.sessionManager = sessionManager;
        this.mainPanel = new JPanel(new BorderLayout(10, 10));
        this.tableModel = new RequestTableModel();
        this.requestTable = new JTable(tableModel);

        initializeUI();

        // Listen for session changes
        sessionManager.addListener(this::updateTable);
    }

    private void initializeUI() {
        // Top panel - controls
        JPanel controlPanel = new JPanel(new BorderLayout(5, 5));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Output directory selection
        JPanel dirPanel = new JPanel(new BorderLayout(5, 5));
        dirPanel.add(new JLabel("Output Directory:"), BorderLayout.WEST);
        outputDirField = new JTextField(System.getProperty("user.home"));
        dirPanel.add(outputDirField, BorderLayout.CENTER);
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> browseDirectory());
        dirPanel.add(browseButton, BorderLayout.EAST);
        controlPanel.add(dirPanel, BorderLayout.NORTH);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton exportButton = new JButton("Export Session");
        exportButton.addActionListener(e -> exportSession());
        buttonPanel.add(exportButton);

        JButton clearButton = new JButton("Clear Session");
        clearButton.addActionListener(e -> clearSession());
        buttonPanel.add(clearButton);

        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(e -> removeSelected());
        buttonPanel.add(removeButton);

        statusLabel = new JLabel("Ready - 0 requests captured");
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(statusLabel);

        controlPanel.add(buttonPanel, BorderLayout.CENTER);

        mainPanel.add(controlPanel, BorderLayout.NORTH);

        // Center panel - split between table and viewers
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3);

        // Table
        JScrollPane tableScrollPane = new JScrollPane(requestTable);
        requestTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        requestTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedRequest();
            }
        });
        splitPane.setTopComponent(tableScrollPane);

        // Request/Response viewers
        JPanel viewerPanel = new JPanel(new GridLayout(1, 2, 5, 5));

        // Request viewer
        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setBorder(BorderFactory.createTitledBorder("Request"));
        requestViewer = api.userInterface().createHttpRequestEditor();
        requestPanel.add(requestViewer.uiComponent(), BorderLayout.CENTER);
        viewerPanel.add(requestPanel);

        // Response viewer
        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("Response"));
        responseViewer = api.userInterface().createHttpResponseEditor();
        responsePanel.add(responseViewer.uiComponent(), BorderLayout.CENTER);
        viewerPanel.add(responsePanel);

        splitPane.setBottomComponent(viewerPanel);
        mainPanel.add(splitPane, BorderLayout.CENTER);
    }

    private void browseDirectory() {
        JFileChooser chooser = new JFileChooser(outputDirField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
            outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void exportSession() {
        String outputDir = outputDirField.getText().trim();
        if (outputDir.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                "Please specify an output directory",
                "Export Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        File dir = new File(outputDir);
        if (!dir.exists() || !dir.isDirectory()) {
            JOptionPane.showMessageDialog(mainPanel,
                "Output directory does not exist",
                "Export Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (sessionManager.exportSession(outputDir)) {
            JOptionPane.showMessageDialog(mainPanel,
                String.format("Successfully exported %d requests to:\n%s",
                    sessionManager.getCount(), outputDir),
                "Export Successful",
                JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(mainPanel,
                "Export failed. Check the extension output for details.",
                "Export Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearSession() {
        if (sessionManager.getCount() == 0) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(mainPanel,
            String.format("Clear all %d captured requests?", sessionManager.getCount()),
            "Clear Session",
            JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            sessionManager.clearSession();
            requestViewer.setRequest(null);
            responseViewer.setResponse(null);
        }
    }

    private void removeSelected() {
        int selectedRow = requestTable.getSelectedRow();
        if (selectedRow >= 0) {
            sessionManager.removeRequest(selectedRow);
            requestViewer.setRequest(null);
            responseViewer.setResponse(null);
        }
    }

    private void displaySelectedRequest() {
        int selectedRow = requestTable.getSelectedRow();
        if (selectedRow >= 0) {
            List<CapturedRequest> requests = sessionManager.getCapturedRequests();
            if (selectedRow < requests.size()) {
                CapturedRequest captured = requests.get(selectedRow);
                requestViewer.setRequest(captured.getRequest());
                if (captured.hasResponse()) {
                    responseViewer.setResponse(captured.getResponse());
                } else {
                    responseViewer.setResponse(null);
                }
            }
        }
    }

    private void updateTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.fireTableDataChanged();
            statusLabel.setText(String.format("Ready - %d requests captured", sessionManager.getCount()));
        });
    }

    public Component getComponent() {
        return mainPanel;
    }

    private class RequestTableModel extends AbstractTableModel {
        private final String[] columnNames = {"#", "Method", "URL", "Timestamp", "Has Response"};
        private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        @Override
        public int getRowCount() {
            return sessionManager.getCount();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<CapturedRequest> requests = sessionManager.getCapturedRequests();
            if (rowIndex >= requests.size()) {
                return "";
            }

            CapturedRequest request = requests.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> request.getOrder();
                case 1 -> request.getMethod();
                case 2 -> request.getUrl();
                case 3 -> request.getTimestamp().atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime().format(timeFormatter);
                case 4 -> request.hasResponse() ? "Yes" : "No";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Integer.class : String.class;
        }
    }
}
