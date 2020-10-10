package com.github.czy211.wowapi.view;

import com.github.czy211.wowapi.constant.EnumVersionType;
import com.github.czy211.wowapi.constant.LinkConst;
import com.github.czy211.wowapi.util.Utils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class BaseApiPane extends BorderPane {
    private static final HashMap<String, String> COMMENTS = new HashMap<>();

    static {
        COMMENTS.put("Region:CreateAnimationGroup", "---@return AnimationGroup\n");
        COMMENTS.put("Frame:CreateFontString", "---@return FontString\n");
        COMMENTS.put("Frame:CreateTexture", "---@return Texture\n");
    }

    private String name;
    private Label lbName;
    private Label lbVersion;
    private EnumVersionType versionType;
    private Label lbStatus;
    private Hyperlink hlLink;
    private Button btDownload;
    private Button btCheck;
    private ProgressBar progressBar;

    public BaseApiPane(String name, EnumVersionType versionType) {
        setPadding(new Insets(5, 10, 2, 10));

        this.name = name;
        this.versionType = versionType;
        lbName = new Label(name);
        lbVersion = new Label();
        lbStatus = new Label();
        hlLink = new Hyperlink();
        HBox centerNode = new HBox(lbVersion, lbStatus);
        btDownload = new Button("下载");
        btCheck = new Button("检查更新");
        progressBar = new ProgressBar(0);
        HBox rightNode = new HBox(5, btDownload);
        if (versionType != EnumVersionType.NONE) {
            rightNode.getChildren().add(btCheck);
        }

        setCenter(centerNode);
        setLeft(lbName);
        setRight(rightNode);
        setBottom(progressBar);

        centerNode.setAlignment(Pos.CENTER);
        rightNode.setAlignment(Pos.CENTER_RIGHT);
        setAlignment(lbName, Pos.CENTER);
        setMargin(centerNode, new Insets(0, 5, 0, 5));
        setMargin(progressBar, new Insets(3, 0, 0, 0));
        progressBar.setVisible(false);
        progressBar.prefWidthProperty().bind(widthProperty());

        hlLink.setOnAction(event -> {
            try {
                Desktop.getDesktop().browse(new URI(hlLink.getText()));
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        });

        AtomicReference<Thread> thread = new AtomicReference<>(new Thread(() -> {
        }));
        AtomicLong threadId = new AtomicLong();

        btDownload.setOnAction(event -> {
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.getId() == threadId.get() && t.getState() == Thread.State.RUNNABLE) {
                    // 中断之前的下载线程
                    t.interrupt();
                    return;
                }
            }

            Platform.runLater(() -> {
                progressBar.setVisible(true);
                lbStatus.setTextFill(Color.BLUE);
                lbStatus.setText("正在连接……");
                centerNode.getChildren().remove(hlLink);
                btDownload.setDisable(true);
                btCheck.setDisable(true);
                progressBar.setProgress(0);
            });

            thread.set(new Thread(() -> {
                try {
                    download();
                    if (Thread.currentThread().isInterrupted()) {
                        // 下载线程被中断
                        Platform.runLater(() -> {
                            lbStatus.setTextFill(Color.RED);
                            lbStatus.setText("已取消下载");
                        });
                    } else {
                        // 下载线程没有被中断，则下载完成
                        Platform.runLater(() -> {
                            lbStatus.setTextFill(Color.GREEN);
                            lbStatus.setText("下载完成");
                        });
                    }
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        lbStatus.setTextFill(Color.RED);
                        lbStatus.setText("下载失败！无法连接到");
                        centerNode.getChildren().add(hlLink);
                        hlLink.setText(e.getMessage());
                        btDownload.setDisable(false);
                    });
                    e.printStackTrace();
                }

                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    btDownload.setText("下载");
                    checkFileExistence();
                });
            }));
            threadId.set(thread.get().getId());
            thread.get().start();
        });

        btCheck.setOnAction(event -> new Thread(() -> {
            Platform.runLater(() -> {
                lbStatus.setTextFill(Color.BLUE);
                lbStatus.setText("检查更新中……");
                centerNode.getChildren().remove(hlLink);
                btDownload.setDisable(true);
                btCheck.setDisable(true);
                progressBar.setVisible(true);
                progressBar.setProgress(-1);
            });

            long localVersion = Long.parseLong(getLocalVersion(new File(Utils.getDownloadPath() + name)));
            try {
                long remoteVersion = getRemoteVersion();
                Platform.runLater(() -> {
                    if (localVersion == remoteVersion) {
                        lbStatus.setTextFill(Color.GREEN);
                        lbStatus.setText("已是最新版本");
                    } else {
                        lbStatus.setTextFill(Color.RED);
                        lbStatus.setText("有新版本可下载");
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    lbStatus.setTextFill(Color.RED);
                    lbStatus.setText("检查更新失败！无法连接到");
                    centerNode.getChildren().add(hlLink);
                    hlLink.setText(e.getMessage());
                });
                e.printStackTrace();
            }

            Platform.runLater(() -> {
                btDownload.setDisable(false);
                btCheck.setDisable(false);
                progressBar.setVisible(false);
            });
        }).start());
    }

    public void connectSuccess() {
        Platform.runLater(() -> {
            btDownload.setDisable(false);
            btDownload.setText("取消下载");
            lbStatus.setText("下载中…… 0%");
        });
    }

    public void updateProgress(double progress) {
        String status = String.format("%s %.1f%%", "下载中……", progress * 100);
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            lbStatus.setText(status);
        });
    }

    public void checkFileExistence() {
        File[] files = new File(Utils.getDownloadPath()).listFiles();
        boolean fileExist = false;
        File filepath = null;
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String filename = file.getName();
                    if (filename.substring(0, filename.lastIndexOf("."))
                            .equals(name.substring(0, name.lastIndexOf(".")))) {
                        fileExist = true;
                        filepath = file;
                        break;
                    }
                }
            }
        }
        if (fileExist) {
            String version = getLocalVersion(filepath);
            if (versionType == EnumVersionType.TIMESTAMP) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm");
                LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(version)),
                        ZoneId.systemDefault());
                lbVersion.setText("当前版本：" + formatter.format(localDateTime));
                btCheck.setDisable("".equals(version));
            } else if (versionType == EnumVersionType.BUILD) {
                lbVersion.setText("当前版本：" + version);
                btCheck.setDisable("".equals(version));
            } else {
                lbVersion.setText(version);
                btCheck.setDisable(true);
            }
        } else {
            lbVersion.setText("文件不存在！");
            btCheck.setDisable(true);
        }
    }

    public String getLocalVersion(File filepath) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filepath),
                    StandardCharsets.UTF_8));
            String version = reader.readLine();
            if (version != null && version.startsWith(EnumVersionType.PREFIX) && versionType != EnumVersionType.NONE) {
                return version.substring(EnumVersionType.PREFIX.length());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public void downloadFxmlFile(String filepath) throws IOException {
        downloadFxmlFile(filepath, null);
    }

    public void downloadFxmlFile(String filepath, String language) throws IOException {
        long fileBuild = getBuild()[0];
        String urlStr = LinkConst.FXML_BASE + "/" + fileBuild + filepath + (language == null ? "" : ("/" + language))
                + "/get";
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(EnumVersionType.PREFIX).append(getRemoteVersion()).append("\n\n");
            URL url = new URL(urlStr);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestProperty("Referer", LinkConst.FXML_BASE + "/" + fileBuild);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.56 Safari/537.36 Edg/79.0.309.40");
            double total = connection.getContentLength();
            int current = 0;
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            connectSuccess();
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                sb.append(line).append("\n");
                current += line.length();
                updateProgress(current / total);
            }
            try (PrintWriter writer = new PrintWriter(Utils.getDownloadPath() + name, "UTF-8")) {
                writer.print(sb);
            }
        } catch (IOException e) {
            throw new IOException(urlStr, e);
        }
    }

    public StringBuilder appendFunction(StringBuilder sb, Element element) {
        Element link = element.selectFirst("a");
        String title = link.attr("title");
        String text = element.text();
        String description = text.replaceAll("\\[", "{").replaceAll("]", "}");
        String url = "";
        if (!title.endsWith("(page does not exist)")) {
            url = "\n---\n--- [" + LinkConst.WIKI_BASE + link.attr("href") + "]";
        }
        String name = link.text();
        sb.append("--- ").append(description).append(url).append("\n");
        if (COMMENTS.get(name) != null) {
            sb.append(COMMENTS.get(name));
        }
        sb.append("function ").append(name);
        StringBuilder after = new StringBuilder("(");
        if (text.indexOf(")") - text.indexOf("(") > 1) {
            after.append("...");
        }
        after.append(") end\n\n");
        sb.append(after);
        return after;
    }


    public long getRemoteTimestamp(String url) throws IOException {
        try {
            Document document = Jsoup.connect(url).get();
            Element element = document.selectFirst("#footer-info-lastmod");
            String dateTime = element.text().substring(29);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, 'at' HH:mm.", Locale.ENGLISH);
            LocalDateTime localDateTime = LocalDateTime.parse(dateTime, formatter);
            ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.systemDefault());
            return zonedDateTime.toEpochSecond();
        } catch (IOException e) {
            throw new IOException(url, e);
        }
    }

    public long getRemoteBuild(String filepath) throws IOException {
        String url = LinkConst.FXML_BASE + "/live";
        try {
            Document document = Jsoup.connect(url).get();
            String filename = filepath.substring(filepath.lastIndexOf("/") + 1);
            Element tr = document.selectFirst("tr:contains(" + filename + ")");
            // 第二个 td 是 build 信息
            Element td = tr.select("td").get(1);
            String text = td.text();
            return Long.parseLong(text.substring(text.length() - 5));
        } catch (IOException e) {
            throw new IOException(url, e);
        }
    }

    /**
     * @return 第一个元素是文件的 build，第二个元素是游戏的 build
     */
    public long[] getBuild() throws IOException {
        String url = LinkConst.FXML_BASE + "/live";
        try {
            Document document = Jsoup.connect(url).get();
            Element element = document.selectFirst("h1");
            long fileBuild = Long.parseLong(element.text().substring(6, 11));
            long gameBuild = fileBuild;
            Element moreBuilds = element.selectFirst(".morebuilds");
            if (moreBuilds != null) {
                String title = moreBuilds.attr("title");
                gameBuild = Long.parseLong(title.substring(1));
            }
            return new long[]{fileBuild, gameBuild};
        } catch (IOException e) {
            throw new IOException(url, e);
        }
    }

    public abstract void download() throws IOException;

    public abstract long getRemoteVersion() throws IOException;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Label getLbName() {
        return lbName;
    }

    public void setLbName(Label lbName) {
        this.lbName = lbName;
    }

    public Label getLbVersion() {
        return lbVersion;
    }

    public void setLbVersion(Label lbVersion) {
        this.lbVersion = lbVersion;
    }

    public EnumVersionType getVersionType() {
        return versionType;
    }

    public void setVersionType(EnumVersionType versionType) {
        this.versionType = versionType;
    }

    public Label getLbStatus() {
        return lbStatus;
    }

    public void setLbStatus(Label lbStatus) {
        this.lbStatus = lbStatus;
    }

    public Hyperlink getHlLink() {
        return hlLink;
    }

    public void setHlLink(Hyperlink hlLink) {
        this.hlLink = hlLink;
    }

    public Button getBtDownload() {
        return btDownload;
    }

    public void setBtDownload(Button btDownload) {
        this.btDownload = btDownload;
    }

    public Button getBtCheck() {
        return btCheck;
    }

    public void setBtCheck(Button btCheck) {
        this.btCheck = btCheck;
    }

    public ProgressBar getProgressBar() {
        return progressBar;
    }

    public void setProgressBar(ProgressBar progressBar) {
        this.progressBar = progressBar;
    }
}
