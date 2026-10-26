package com.commit.project3.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Component
public class UploadUtil {

    // application.yaml의 file.upload.dir 값
    @Value("${file.upload.dir}")
    private String uploadDir;

    // 파일을 업로드 폴더에 저장하고, 저장된 파일명을 돌려줌
    // 같은 이름의 파일이 덮어써지지 않도록 UUID로 이름을 새로 만들고 확장자만 유지
    public String saveFile(MultipartFile file) throws IOException {
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String saveName = UUID.randomUUID().toString() + getExt(file.getOriginalFilename());
        file.transferTo(new File(dir, saveName));
        return saveName;
    }

    // 확장자 (점 포함, 소문자). 없으면 빈 문자열
    public static String getExt(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
    }
}
