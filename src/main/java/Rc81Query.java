import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Rc81Query {

    private final String year;
    private final String lastThree;
    private final String name;
    private final int start;

    File rc81Dir;
    File rc81Captcha;
    File rc81CaptchaCode;
    File rc81CaptchaResult;
    private int retryCount = 0;

    private final String cookie = "JSESSIONID=98A93C2A8DE0CEEC0DF0BE9BD8FFF466; SERVERID=6850353abc2dc518cb9088ef026e1e1f|1603719311|1603714287";

    public Rc81Query(String year, String lastThree, String name, int start) {
        this.year = year;
        this.lastThree = lastThree;
        this.name = name;
        this.start = start;

        rc81Dir = new File("./rc81");
        rc81Captcha = new File("./rc81/captcha");
        rc81CaptchaCode = new File("./rc81/captchaCode");
        rc81CaptchaResult = new File("./rc81/captchaResult");

        if (!rc81Dir.exists()) {
            rc81Dir.mkdir();
        }

        if (!rc81Captcha.exists()) {
            rc81Captcha.mkdir();
        }

        if (!rc81CaptchaCode.exists()) {
            rc81CaptchaCode.mkdir();
        }

        if (!rc81CaptchaResult.exists()) {
            rc81CaptchaResult.mkdir();
        }
    }

    private void tryQuery(String number) {
        if (isExist()) {
            return;
        }

        CloseableHttpClient client = HttpClients.createDefault();
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("jsessionid", "796a0fa25f7c9ffb"));
        params.add(new BasicNameValuePair("kkny", "23e786fc39081f4b"));
        params.add(new BasicNameValuePair("msg", ""));
        params.add(new BasicNameValuePair("zjhm", number));
        params.add(new BasicNameValuePair("ksxm", name));
        params.add(new BasicNameValuePair("yzm", getCaptcha("http://182.92.48.145/qjwzwb/manage/yzm.jsp", number)));

        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, Consts.UTF_8);
        HttpPost httpPost = new HttpPost("http://182.92.48.145/qjwzwb/score/qscore.htm");
        httpPost.setHeader("Cookie", cookie);
        httpPost.setHeader("Connection", "keep-alive");
        httpPost.setEntity(entity);
        try {
            CloseableHttpResponse response = client.execute(httpPost);
            String html = EntityUtils.toString(response.getEntity());
//            System.out.println(html);
            Document document = Jsoup.parse(html);
            Element element = document.getElementById("msg");
            if (element == null) {
                retryCount = 0;
                File htmlFile = new File(rc81CaptchaResult.getAbsolutePath() + "/" + number + ".html");
                FileWriter os = new FileWriter(htmlFile);
                BufferedWriter bos = new BufferedWriter(os);
                bos.write(html);
                bos.close();
            } else {
                System.out.println("code: "+element.val());
                if ("验证码不正确！".equals(element.val()) && retryCount < 3) {
                    retryCount++;
                    tryQuery(number);
                } else {
                    retryCount = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isExist() {
        String[] list = rc81CaptchaResult.list();
        if (list.length != 0) {
            for (String file : list) {
                int length = file.length();
                String last = file.substring(length - 8, length - 5);
                if (last.equals(lastThree)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getCaptcha(String link, String number) {
        try {
            URL url = new URL(link);
            File jpg = new File(rc81Captcha.getAbsolutePath() + "/" + number + ".jpg");
            if (jpg.exists()) {
                jpg.delete();
            }
            File text = new File(rc81CaptchaCode.getAbsolutePath() + "/" + number + ".txt");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Cookie", cookie);
            connection.connect();
            int statusCode = connection.getResponseCode();
            if (statusCode < 400) {
                OutputStream os = new FileOutputStream(jpg);
                InputStream is = connection.getInputStream();
                byte[] buff = new byte[1024];
                while (true) {
                    int read = is.read(buff);
                    if (read == -1) {
                        break;
                    }
                    byte[] tmp = new byte[read];
                    System.arraycopy(buff, 0, tmp, 0, read);
                    os.write(tmp);
                }
                os.close();
                is.close();

                String command = "tesseract.exe" + " " + jpg.getAbsolutePath() + " " + text.getAbsolutePath().substring(0, text.getAbsolutePath().length() - 4);
                System.out.println(command);
                Process process = Runtime.getRuntime().exec(command);
                int execCode = process.waitFor();
                if (execCode == 0) {
                    InputStream fis = new FileInputStream(text);
                    InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    String result = br.readLine();
                    br.close();
                    return result;
                } else {
                    throw new RuntimeException("fail to get captcha code");
                }
            } else {
                throw new RuntimeException("can not get captcha image");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public void start() {
        for (int i = start; i < 200; i++) {
            String numberI;
            if (i < 10) {
                numberI = "00" + i;
            } else if (i < 100) {
                numberI = "0" + i;
            } else {
                numberI = "" + i;
            }

            tryQuery(year + numberI + "00" + lastThree);
            tryQuery(year + numberI + "01" + lastThree);
            tryQuery(year + numberI + "02" + lastThree);
        }
    }

    public static void main(String[] args) {
//        Rc81Query query6 = new Rc81Query("2020", "993", "翁诚", 73);
//        query6.tryQuery("202007200993");
        Rc81Query query6 = new Rc81Query("2020", "793", "王智慧", 73);
        query6.start();
        Rc81Query query7 = new Rc81Query("2020", "796", "张芊", 73);
        query7.start();
    }
}
