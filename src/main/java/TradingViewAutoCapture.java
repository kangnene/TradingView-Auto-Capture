import com.microsoft.playwright.*;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.LocalDate;

public class TradingViewAutoCapture {
    public static void main(String[] args) {
        if (args.length == 0) return;

        String[] symbols = args[0].split(",");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            // auth.json을 사용하여 로그인 세션 유지
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setStorageStatePath(Paths.get("auth.json")));
            Page page = context.newPage();

            // 1. 날짜 폴더 경로 설정 (예: screenshots/2026-03-05)
            String dateFolder = LocalDate.now().toString();
            String baseDir = "screenshots/" + dateFolder;

            // 2. 폴더가 없으면 생성
            Files.createDirectories(Paths.get(baseDir));

            for (String symbol : symbols) {
                String targetSymbol = symbol.trim();
                System.out.println("Capturing: " + targetSymbol);

                // 트레이딩뷰 차트 페이지 접속 (심볼 포함)
                page.navigate("https://www.tradingview.com/chart/?symbol=" + targetSymbol);
                
                // 차트 로딩 대기 (필요시 시간 조절)
                page.waitForTimeout(5000); 

                // 3. 날짜 폴더 안에 종목이름으로 저장
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get(baseDir + "/" + targetSymbol + ".jpg")));
            }
            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
