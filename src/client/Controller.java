package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    private ListView<String> clientList; //добавили <String>, а то при стандарте не добавляется функционал. Бех него просто объекты хранятся, а нам надо String
    @FXML
    private TextArea textArea;
    @FXML
    private TextField textField;
    @FXML
    private HBox authPanel;
    @FXML
    private TextField loginField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private HBox msgPanel;

    private final String IP_ADDRESS = "localhost";
    private final int PORT = 8189;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private Stage stage;
    private Stage regStage;
    private RegController regController;
    
    private boolean authenticated;
    private String nickname;

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        authPanel.setVisible(!authenticated);
        authPanel.setManaged(!authenticated);
        msgPanel.setVisible(authenticated);
        msgPanel.setManaged(authenticated);
        clientList.setVisible(authenticated);
        clientList.setManaged(authenticated);

        if (!authenticated) {
            nickname = "";
            setTitle("Балабол");
        } else {
            setTitle(String.format("[ %s ] - Балабол", nickname));
        }
        textArea.clear();

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            stage = (Stage) textField.getScene().getWindow();

            stage.setOnCloseRequest(event -> { // отключаться от сервера, если авторизован, при нажатии на крестик
                if(socket != null && !socket.isClosed()){
                    try {
                        out.writeUTF("/end");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        });
        setAuthenticated(false);
        createRegWindow();
    }

    private void connect() {
        try {
            socket = new Socket(IP_ADDRESS, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/regok ")) {
                            regController.addMessageTextArea("Регистрация прошла успешно");
                        }
                        if (str.startsWith("/regno ")) {
                            regController.addMessageTextArea("Регистрация не прошла");
                        }

                        if (str.startsWith("/authok ")) {
                            nickname = str.split("\\s")[1];
                            setAuthenticated(true);
                            break;
                        }

                        textArea.appendText(str + "\n");
                    }

                    //цикл работы
                    while (true) {
                        String str = in.readUTF();
                        if (str.startsWith("/")) {
                            if (str.equals("/end")) {
                                break;
                            }
                            if (str.startsWith("/clientlist")) { // обновляем лист с авторизованными пользователями
                                String[] token = str.split("\\s");
                                Platform.runLater(() -> { // так как графическую часть затрагиваем, используем это
                                    clientList.getItems().clear(); // очищаем поле с никами, кто авторизовался
                                    for (int i = 1; i < token.length; i++) { // /clientlist qwe asd zxc - это 0 1 2 3 элементы, нам надо с 1 элемента
                                        clientList.getItems().add(token[i]); // добавляем в поле с никами авторизованные ники

                                    }
                                });
                            }
                        } else {
                            textArea.appendText(str + "\n");
                        }
                    }
                }catch (EOFException e){
                    System.out.println("Отключен по таймауту");
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    setAuthenticated(false);
                    try {
                        socket.close();
                        in.close();
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(ActionEvent actionEvent) {
        if (textField.getText().trim().length() == 0) {
            return;
        }
        try {
            out.writeUTF(textField.getText());
            textField.clear();
            textField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void tryToAuth(ActionEvent actionEvent) {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        String msg = String.format("/auth %s %s",
                loginField.getText().trim(), passwordField.getText().trim());
        try {
            out.writeUTF(msg);
            passwordField.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setTitle(String title) {
        Platform.runLater(() -> {
            stage.setTitle(title);
        });
    }

    public void clickClientList(MouseEvent mouseEvent) { // куча методов у mouseEvent. (точка). Посмотреть документацию!!! Попробовать самому, поэкспериментировать
        textField.setText(String.format("/w %s ", clientList.getSelectionModel().getSelectedItem())); // находим никнейм в списке авторизованных пользователей
    }

    private void createRegWindow() {

        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("reg.fxml")); // надо создать новый FXML loader
            Parent root = fxmlLoader.load();
            regStage = new Stage();
            regStage.setTitle("Регистрация");
            regStage.setScene(new Scene(root, 400, 300));
            regStage.initModality(Modality.APPLICATION_MODAL); // нельзя перейти в то окно, из которого это окно было вызвано
            regController = fxmlLoader.getController(); // создание ссылки на RegController
            regController.setController(this); // установить в RegController ссылку на Controller
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void regStageShow(ActionEvent actionEvent) {
        regStage.show(); // показать окно при нажатии на кнопку "reg"
    }

    public void tryRegistration(String login, String password, String nickname){
        String message = String.format("/reg %s %s %s", login, password, nickname);
        if (socket == null || socket.isClosed()) {
            connect();
        }
        try {
            out.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
