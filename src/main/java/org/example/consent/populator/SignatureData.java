package org.example.consent.populator;

import java.util.Date;

public class SignatureData {
    private byte[] imageData;
    private String format;
    private String signerName;
    private Date signedDate;
    private String ipAddress;
    private String userAgent;

    // Getters and setters
    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }

    public Date getSignedDate() { return signedDate; }
    public void setSignedDate(Date signedDate) { this.signedDate = signedDate; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
