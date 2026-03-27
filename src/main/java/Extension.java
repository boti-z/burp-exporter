import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        // Set extension name
        api.extension().setName("Request Saver");

        // Initialize session manager
        SessionManager sessionManager = new SessionManager(api);

        // Register UI tab
        RequestSaverTab tab = new RequestSaverTab(api, sessionManager);
        api.userInterface().registerSuiteTab("Request Saver", tab.getComponent());

        // Register context menu
        ContextMenuProvider contextMenu = new ContextMenuProvider(api, sessionManager);
        api.userInterface().registerContextMenuItemsProvider(contextMenu);

        // Log successful initialization
        api.logging().logToOutput("Request Saver extension loaded successfully");
        api.logging().logToOutput("Right-click any HTTP request and select 'Add to Request Saver'");
        api.logging().logToOutput("Use the 'Request Saver' tab to manage and export your session");
    }
}