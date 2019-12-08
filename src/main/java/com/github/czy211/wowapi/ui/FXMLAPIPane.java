package com.github.czy211.wowapi.ui;

import com.github.czy211.wowapi.constant.Constants;
import com.github.czy211.wowapi.i18n.I18n;
import com.github.czy211.wowapi.model.FXMLPage;
import com.github.czy211.wowapi.util.Utils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FXMLAPIPane extends APIPane {
    private FXMLPage page;
    private String statusText;

    public FXMLAPIPane(FXMLPage page) {
        super(page);
        this.page = page;
        setStatusText();
    }

    @Override
    public void checkForUpdate() {
        String url = Constants.FXML_BASE_URL + "/live";
        try {
            // 获取远程 build 号
            Document document = Jsoup.connect(url).get();
            int build = Utils.getBuild(document);
            // 获取状态类型
            int statusType = getStatusType();
            // 有文件不存在或版本不一致或本地 build 号小于远程 build 号时提示可以更新
            if (statusType == 0 || statusType == 2 || getBuildFromFile(new File(
                    Utils.getOutputDirectory() + "/" + page.getFileNames()[0])) < build) {
                updateStatus(I18n.getText("status_update_available"));
            } else {
                updateStatus(I18n.getText("status_latest_version") + "        build: " + build);
            }
        } catch (IOException e) {
            updateStatus(I18n.getText("status_connect_fail", url));
            e.printStackTrace();
        }
    }

    @Override
    public void setStatusText() {
        getStatusType();
        updateStatus(statusText);
    }

    /**
     * 获取状态类型
     *
     * @return 状态类型
     */
    private int getStatusType() {
        StringBuilder text = new StringBuilder();
        int build = -1;
        String[] fileNames = page.getFileNames();
        for (String fileName : fileNames) {
            File file = new File(Utils.getOutputDirectory() + "/" + fileName);
            if (!file.exists()) {
                text.append(fileName).append(" ");
            } else if (text.length() == 0) {
                int fileBuild = getBuildFromFile(file);
                if (build != -1 && build != fileBuild) {
                    statusText = I18n.getText("status_version_different");
                    return 2;
                }
                build = fileBuild;
            }
        }
        if (text.length() > 0) {
            // 有文件不存在
            statusText = I18n.getText("status_file_not_exist", text);
            return 0;
        }
        // 文件都存在且版本一致
        statusText = "build: " + build;
        return 1;
    }

    /**
     * 获取本地文件的 build 号
     *
     * @param file 本地文件
     * @return build 号
     */
    private int getBuildFromFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return Integer.parseInt(reader.readLine().substring(Constants.COMMENT_BUILD.length()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
