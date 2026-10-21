package com.trading.ui;

import com.trading.auth.AuthService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * 登录界面：区分管理员与普通用户。
 *
 * <p>登录成功后由 {@code TradingPlatformApplication} 按角色打开对应窗口；
 * 若该账号已在别处登录，旧终端会被 {@code Session#invalidate} 强制跳回本界面。
 */
public class LoginView {

    /** 登录回调：用户名 + 密码 */
    public interface LoginCallback {
        void login(String username, String password);
    }

    private final Scene scene;
    private Label messageLabel;

    public LoginView(AuthService authService, LoginCallback callback, String message) {
        Label title = new Label("多智能体金融模拟平台");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        messageLabel = new Label(message == null || message.isEmpty() ? "请使用演示账号登录" : message);
        messageLabel.setStyle("-fx-text-fill: #b45309;");

        TextField userField = new TextField();
        userField.setPromptText("用户名");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("密码");

        Button loginButton = new Button("登 录");
        loginButton.setDefaultButton(true);
        loginButton.setMaxWidth(Double.MAX_VALUE);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setAlignment(Pos.CENTER);
        form.add(new Label("用户名："), 0, 0);
        form.add(userField, 1, 0);
        form.add(new Label("密　码："), 0, 1);
        form.add(passwordField, 1, 1);

        Runnable attempt = () -> {
            String username = userField.getText() == null ? "" : userField.getText().trim();
            String password = passwordField.getText();
            if (username.isEmpty()) {
                messageLabel.setText("请输入用户名");
                return;
            }
            callback.login(username, password);
        };
        loginButton.setOnAction(event -> attempt.run());
        passwordField.setOnAction(event -> attempt.run());

        Label hint = new Label("演示账号：admin/admin123（管理员）  trader/trader123、Alice/alice123（普通用户）");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        VBox root = new VBox(12, title, messageLabel, form, loginButton, hint);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setPrefWidth(460);

        this.scene = new Scene(root, 480, 300);
    }

    public Scene getScene() {
        return scene;
    }
}
