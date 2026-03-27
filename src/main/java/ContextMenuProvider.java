import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ContextMenuProvider implements ContextMenuItemsProvider {
    private final MontoyaApi api;
    private final SessionManager sessionManager;

    public ContextMenuProvider(MontoyaApi api, SessionManager sessionManager) {
        this.api = api;
        this.sessionManager = sessionManager;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> menuItems = new ArrayList<>();

        // Only show menu item if there are selected messages
        if (event.messageEditorRequestResponse().isPresent() ||
            !event.selectedRequestResponses().isEmpty()) {

            JMenuItem menuItem = new JMenuItem("Add to Request Saver");
            menuItem.addActionListener(e -> addToSaver(event));
            menuItems.add(menuItem);

            // If multiple items selected, show count
            int count = event.selectedRequestResponses().size();
            if (count > 1) {
                JMenuItem multiItem = new JMenuItem(
                    String.format("Add all %d requests to Request Saver", count));
                multiItem.addActionListener(e -> addAllToSaver(event));
                menuItems.add(multiItem);
            }
        }

        return menuItems;
    }

    private void addToSaver(ContextMenuEvent event) {
        // Try to get from message editor first
        if (event.messageEditorRequestResponse().isPresent()) {
            var messageEditor = event.messageEditorRequestResponse().get();
            sessionManager.addRequest(
                messageEditor.requestResponse().request(),
                messageEditor.requestResponse().response()
            );
            return;
        }

        // Otherwise get from selected items
        if (!event.selectedRequestResponses().isEmpty()) {
            HttpRequestResponse selected = event.selectedRequestResponses().get(0);
            sessionManager.addRequest(selected.request(), selected.response());
        }
    }

    private void addAllToSaver(ContextMenuEvent event) {
        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        int added = 0;

        // Reverse the list to maintain chronological order
        // Burp returns selected items in reverse chronological order
        List<HttpRequestResponse> reversed = new ArrayList<>(selected);
        java.util.Collections.reverse(reversed);

        for (HttpRequestResponse reqResp : reversed) {
            sessionManager.addRequest(reqResp.request(), reqResp.response());
            added++;
        }

        api.logging().logToOutput(String.format("Added %d requests to session", added));
    }
}
