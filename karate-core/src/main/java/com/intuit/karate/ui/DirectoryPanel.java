package com.intuit.karate.ui;

import ch.qos.logback.classic.Logger;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.slf4j.LoggerFactory;

import java.io.File;

import static com.intuit.karate.ui.App.PADDING_INSET;

public class DirectoryPanel extends BorderPane {

    private final TreeView<String> treeView = new TreeView<String>();
    private final ScrollPane scrollPane = new ScrollPane();
    private final App app ;
    private final String envString ;
    private final Stage stage;
    private final Logger logger;


    EventHandler<MouseEvent> mouseEventHandle = (MouseEvent event) -> {
        handleMouseClicked(event);
    };

    public DirectoryPanel(App app, String envString, Stage primaryStage) {
        this.app = app;
        this.envString = envString;
        this.stage = primaryStage;
        logger = (Logger) LoggerFactory.getLogger(DirectoryPanel.class);
    }

    private void handleMouseClicked(MouseEvent event) {
        Node node = event.getPickResult().getIntersectedNode();
        if (node instanceof Text || (node instanceof TreeCell && ((TreeCell) node).getText() != null)) {
            final TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
            if(selectedItem instanceof DirectoryFileItem) {
                File file = openFeatureFile(selectedItem);
                logger.info("Node click: " + file.getName());
            }
        }
    }

    private File openFeatureFile(TreeItem<String> selectedItem) {
        File file = ((DirectoryFileItem) selectedItem).getFile();
        if(file.getName().endsWith(".feature")) {
            this.app.initUi(file, this.envString, this.stage);
        }
        return file;
    }

    public void init(File choice) {
        this.setPadding(PADDING_INSET);
        treeView.setRoot(getNodesForDirectory(choice));
        treeView.addEventHandler(MouseEvent.MOUSE_CLICKED, mouseEventHandle);

        treeView.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode().equals(KeyCode.ENTER)) {
                        final TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
                        if (selectedItem instanceof DirectoryFileItem) {
                            openFeatureFile(selectedItem);
                        } else {
                            selectedItem.setExpanded(true);
                        }
                    }
                }
        );

        scrollPane.setContent(treeView);
        this.setCenter(scrollPane);
        scrollPane.setFitToWidth(true);
    }

    public TreeItem<String> getNodesForDirectory(File directory) {
        TreeItem<String> root = new TreeItem<String>(directory.getName());
        for(File file : directory.listFiles()) {
            System.out.println("Loading " + file.getName());
            if(file.isDirectory()) {
                root.getChildren().add(getNodesForDirectory(file));
            } else {
                final DirectoryFileItem treeItem = new DirectoryFileItem(file);
                root.getChildren().add(treeItem);
            }
        }
        return root;
    }

    public class DirectoryFileItem extends TreeItem {
        private final File file;

        public DirectoryFileItem(File file) {
            super(file.getName());
            this.file = file;
        }

        public File getFile() {
            return file;
        }

    }
}
