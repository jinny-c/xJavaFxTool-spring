package com.xwintop.xJavaFxTool.controller;

import com.xwintop.xJavaFxTool.common.logback.ConsoleLogAppender;
import com.xwintop.xJavaFxTool.controller.index.PluginManageController;
import com.xwintop.xJavaFxTool.model.ToolFxmlLoaderConfiguration;
import com.xwintop.xJavaFxTool.services.IndexService;
import com.xwintop.xJavaFxTool.services.index.PluginManageService;
import com.xwintop.xJavaFxTool.services.index.SystemSettingService;
import com.xwintop.xJavaFxTool.utils.Config;
import com.xwintop.xJavaFxTool.utils.SpringUtil;
import com.xwintop.xJavaFxTool.utils.XJavaFxSystemUtil;
import com.xwintop.xJavaFxTool.view.IndexView;
import com.xwintop.xcore.util.ConfigureUtil;
import com.xwintop.xcore.util.HttpClientUtil;
import com.xwintop.xcore.util.javafx.AlertUtil;
import com.xwintop.xcore.util.javafx.JavaFxSystemUtil;
import com.xwintop.xcore.util.javafx.JavaFxViewUtil;
import de.felixroske.jfxsupport.AbstractFxmlView;
import de.felixroske.jfxsupport.FXMLController;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.xwintop.xJavaFxTool.utils.Config.Keys.NotepadEnabled;
import static com.xwintop.xcore.util.javafx.JavaFxViewUtil.setControllerOnCloseRequest;

/**
 * @ClassName: IndexController
 * @Description: 主页
 * @author: xufeng
 * @date: 2017年7月20日 下午1:50:00
 */
@FXMLController
@Slf4j
@Getter
@Setter
public class IndexController extends IndexView {
    public static final String QQ_URL = "https://support.qq.com/product/127577";
    public static final String STATISTICS_URL = "https://xwintop.gitee.io/maven/tongji/xJavaFxTool.html";

    private Map<String, Menu> menuMap = new HashMap<String, Menu>();
    private Map<String, MenuItem> menuItemMap = new HashMap<String, MenuItem>();
    private IndexService indexService = new IndexService(this);
    private ContextMenu contextMenu = new ContextMenu();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.bundle = resources;
        initView();
        initEvent();
        initService();

        initNotepad();
        this.indexService.addWebView("欢迎吐槽", QQ_URL, null);
        this.tongjiWebView.getEngine().load(STATISTICS_URL);
    }

    private void initNotepad() {
        if (Config.getBoolean(NotepadEnabled, true)) {
            addNodepadAction(null);
        }
    }

    private void initView() {
        menuMap.put("toolsMenu", toolsMenu);
        menuMap.put("moreToolsMenu", moreToolsMenu);
        File libPath = new File("libs/");
        // 获取所有的.jar和.zip文件
        File[] jarFiles = libPath.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles != null) {
            for (File jarFile : jarFiles) {
                if (!PluginManageService.isPluginEnabled(jarFile.getName())) {
                    continue;
                }
                try {
                    this.addToolMenu(jarFile);
                } catch (Exception e) {
                    log.error("加载工具出错：", e);
                }
            }
        }
    }

    private void initEvent() {
        myTextField.textProperty().addListener((observable, oldValue, newValue) -> selectAction(newValue));
        myButton.setOnAction(arg0 -> {
            selectAction(myTextField.getText());
        });
    }

    private void initService() {
    }

    public void addToolMenu(File file) throws Exception {
        XJavaFxSystemUtil.addJarClass(file);
        Map<String, ToolFxmlLoaderConfiguration> toolMap = new HashMap<>();
        List<ToolFxmlLoaderConfiguration> toolList = new ArrayList<>();

        try (JarFile jarFile = new JarFile(file)) {
            JarEntry entry = jarFile.getJarEntry("config/toolFxmlLoaderConfiguration.xml");
            if (entry == null) {
                return;
            }
            InputStream input = jarFile.getInputStream(entry);
            SAXReader saxReader = new SAXReader();
            Document document = saxReader.read(input);
            Element root = document.getRootElement();
            List<Element> elements = root.elements("ToolFxmlLoaderConfiguration");
            for (Element configurationNode : elements) {
                ToolFxmlLoaderConfiguration toolFxmlLoaderConfiguration = new ToolFxmlLoaderConfiguration();
                List<Attribute> attributes = configurationNode.attributes();
                for (Attribute configuration : attributes) {
                    BeanUtils.copyProperty(toolFxmlLoaderConfiguration, configuration.getName(), configuration.getValue());
                }
                List<Element> childrenList = configurationNode.elements();
                for (Element configuration : childrenList) {
                    BeanUtils.copyProperty(toolFxmlLoaderConfiguration, configuration.getName(), configuration.getStringValue());
                }
                if (StringUtils.isEmpty(toolFxmlLoaderConfiguration.getMenuParentId())) {
                    toolFxmlLoaderConfiguration.setMenuParentId("moreToolsMenu");
                }
                if (toolFxmlLoaderConfiguration.getIsMenu()) {
                    if (menuMap.get(toolFxmlLoaderConfiguration.getMenuId()) == null) {
                        toolMap.putIfAbsent(toolFxmlLoaderConfiguration.getMenuId(), toolFxmlLoaderConfiguration);
                    }
                } else {
                    toolList.add(toolFxmlLoaderConfiguration);
                }
            }
        }
        toolList.addAll(toolMap.values());
        this.addMenu(toolList);
    }

    private void addMenu(List<ToolFxmlLoaderConfiguration> toolList) {
        for (ToolFxmlLoaderConfiguration toolConfig : toolList) {
            try {
                if (StringUtils.isEmpty(toolConfig.getResourceBundleName())) {
                    if (StringUtils.isNotEmpty(bundle.getString(toolConfig.getTitle()))) {
                        toolConfig.setTitle(bundle.getString(toolConfig.getTitle()));
                    }
                } else {
                    ResourceBundle resourceBundle = ResourceBundle.getBundle(toolConfig.getResourceBundleName(), Config.defaultLocale);
                    if (StringUtils.isNotEmpty(resourceBundle.getString(toolConfig.getTitle()))) {
                        toolConfig.setTitle(resourceBundle.getString(toolConfig.getTitle()));
                    }
                }
            } catch (Exception e) {
                log.error("加载菜单失败", e);
            }
            if (toolConfig.getIsMenu()) {
                Menu menu = new Menu(toolConfig.getTitle());
                if (StringUtils.isNotEmpty(toolConfig.getIconPath())) {
                    ImageView imageView = new ImageView(new Image(toolConfig.getIconPath()));
                    imageView.setFitHeight(18);
                    imageView.setFitWidth(18);
                    menu.setGraphic(imageView);
                }
                menuMap.put(toolConfig.getMenuId(), menu);
            }
        }

        for (ToolFxmlLoaderConfiguration toolConfig : toolList) {
            if (toolConfig.getIsMenu()) {
                menuMap.get(toolConfig.getMenuParentId()).getItems().add(menuMap.get(toolConfig.getMenuId()));
            }
        }

        for (ToolFxmlLoaderConfiguration toolConfig : toolList) {
            if (toolConfig.getIsMenu()) {
                continue;
            }
            MenuItem menuItem = new MenuItem(toolConfig.getTitle());
            if (StringUtils.isNotEmpty(toolConfig.getIconPath())) {
                ImageView imageView = new ImageView(new Image(toolConfig.getIconPath()));
                imageView.setFitHeight(18);
                imageView.setFitWidth(18);
                menuItem.setGraphic(imageView);
            }
            if ("Node".equals(toolConfig.getControllerType())) {
                menuItem.setOnAction((ActionEvent event) -> {
                    indexService.addContent(menuItem.getText(), toolConfig.getUrl(), toolConfig.getResourceBundleName(), toolConfig.getIconPath());
                });
                if (toolConfig.getIsDefaultShow()) {
                    indexService.addContent(menuItem.getText(), toolConfig.getUrl(), toolConfig.getResourceBundleName(), toolConfig.getIconPath());
                }
            } else if ("WebView".equals(toolConfig.getControllerType())) {
                menuItem.setOnAction((ActionEvent event) -> {
                    indexService.addWebView(menuItem.getText(), toolConfig.getUrl(), toolConfig.getIconPath());
                });
                if (toolConfig.getIsDefaultShow()) {
                    indexService.addWebView(menuItem.getText(), toolConfig.getUrl(), toolConfig.getIconPath());
                }
            }
            menuMap.get(toolConfig.getMenuParentId()).getItems().add(menuItem);
            menuItemMap.put(menuItem.getText(), menuItem);
        }
    }

    public void selectAction(String selectText) {
        if (contextMenu.isShowing()) {
            contextMenu.hide();
        }
        contextMenu = indexService.getSelectContextMenu(selectText);
        contextMenu.show(myTextField, null, 0, myTextField.getHeight());
    }

    @FXML
    private void exitAction(ActionEvent event) {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void closeAllTabAction(ActionEvent event) {
        tabPaneMain.getTabs().clear();
    }

    @FXML
    private void openAllTabAction(ActionEvent event) {
        for (MenuItem value : menuItemMap.values()) {
            value.fire();
        }
    }

    @FXML
    private void addNodepadAction(ActionEvent event) {
        indexService.addNodepadAction(event);
    }

    @FXML
    private void addLogConsoleAction(ActionEvent event) {
        indexService.addLogConsoleAction(event);
    }

    @FXML
    private void pluginManageAction() throws Exception {
        FXMLLoader fXMLLoader = PluginManageController.getFXMLLoader();
        Parent root = fXMLLoader.load();
        PluginManageController pluginManageController = fXMLLoader.getController();
        pluginManageController.setIndexController(this);
        JavaFxViewUtil.openNewWindow(bundle.getString("plugin_manage"), root);
    }

    /**
     * @Title: addContent
     * @Description: 添加Content内容
     */
    private void addContent(String title, String className, String iconPath) {
        try {
//			Class<AbstractFxmlView> viewClass = (Class<AbstractFxmlView>) ClassLoader.getSystemClassLoader().loadClass(className);
            Class<AbstractFxmlView> viewClass = (Class<AbstractFxmlView>) Thread.currentThread().getContextClassLoader().loadClass(className);
            AbstractFxmlView fxmlView = SpringUtil.getBean(viewClass);
            if (singleWindowBootCheckBox.isSelected()) {
//				Main.showView(viewClass, Modality.NONE);
                Stage newStage = JavaFxViewUtil.getNewStage(title, iconPath, fxmlView.getView());
                newStage.setOnCloseRequest((WindowEvent event) -> {
                    setControllerOnCloseRequest(fxmlView.getPresenter(), event);
                });
                return;
            }
            Tab tab = new Tab(title);
            tab.setContent(fxmlView.getView());

            if (StringUtils.isNotEmpty(iconPath)) {
                ImageView imageView = new ImageView(new Image(iconPath));
                imageView.setFitHeight(18);
                imageView.setFitWidth(18);
                tab.setGraphic(imageView);
            }
            tabPaneMain.getTabs().add(tab);
            tabPaneMain.getSelectionModel().select(tab);
            tab.setOnCloseRequest((Event event) -> {
                setControllerOnCloseRequest(fxmlView.getPresenter(), event);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void SettingAction() {
        SystemSettingService.openSystemSettings(bundle.getString("Setting"));
    }

    @FXML
    private void aboutAction(ActionEvent event) throws Exception {
        AlertUtil.showInfoAlert(bundle.getString("aboutText") + Config.xJavaFxToolVersions);
    }

    @FXML
    private void setLanguageAction(ActionEvent event) throws Exception {
        MenuItem menuItem = (MenuItem) event.getSource();
        indexService.setLanguageAction(menuItem.getText());
    }

    @FXML
    private void openLogFileAction() {
        String filePath = "logs/logFile." + DateFormatUtils.format(new Date(), "yyyy-MM-dd") + ".log";
        JavaFxSystemUtil.openDirectory(filePath);
    }

    @FXML
    private void openLogFolderAction() {
        JavaFxSystemUtil.openDirectory("logs/");
    }

    @FXML
    private void openConfigFolderAction() {
        JavaFxSystemUtil.openDirectory(ConfigureUtil.getConfigurePath());
    }

    @FXML
    private void openPluginFolderAction() {
        JavaFxSystemUtil.openDirectory("libs/");
    }

    @FXML
    private void xwintopLinkOnAction() throws Exception {
        HttpClientUtil.openBrowseURLThrowsException("https://gitee.com/xwintop/xJavaFxTool-spring");
    }

    @FXML
    private void userSupportAction() throws Exception {
        HttpClientUtil.openBrowseURLThrowsException("https://support.qq.com/product/127577");
    }
}
