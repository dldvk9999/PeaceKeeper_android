package peacekeeper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    @SuppressLint("StaticFieldLeak")
    public static Context mContext;

    // 제스쳐 인식 관련
    GestureDetector detector;

    // 웹뷰 관련 (길이 관련까지 모두 포함)
    WebView webView;
    TextView logo;
    int tempY                   = 0;
    long tempTime               = 0;
    float oldY                  = 0;
    String destText             = "I LOVE YOU";

    // 스크린 텍스트 관련
    int OrigLen                 = 0;
    boolean containScreenText   = false;
    String[] ScreenTextOrig;
    String[] ScreenText;
    float dY = 0;

    // 실시간 타이머
    Timer timer         = new Timer();
    TimerTask TT        = null;

    // tflite 관련
    private static final String SIMPLE_SPACE_OR_PUNCTUATION = "[ ,.'\"<>;:/=+()~`@#$%^&*\\-\\[\\]!?]";
    float[][] output = new float[1][2];
    Interpreter lite;

    @SuppressWarnings("deprecation")
    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("peacekeeper");
        mContext = this;

        /* tflite 파일 load. */
        getTfliteInterpreter();

        /* 레이아웃 뷰 및 버튼 선언 */
        final Button btn_exit           = findViewById(R.id.btn_exit);
        Button urlChange                = findViewById(R.id.urlChange);
        final LinearLayout btnLayout    = findViewById(R.id.btn_layout);
        webView                         = findViewById(R.id.webView);
        logo                            = findViewById(R.id.logo);
        btnLayout.setBackgroundResource(R.drawable.background);

        /* 웹뷰 초기화 */
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webView.setWebViewClient(new WebViewClientClass());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");

        //noinspection deprecation
        @SuppressLint("HandlerLeak")
        final Handler handler = new Handler() {
            public void handleMessage(@SuppressWarnings("NullableProblems") Message msg) {
                processToText();
            }
        };

        urlChange.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle("URL을 선택하세요.");
            builder.setItems(R.array.URL_List, (dialog, pos) -> {
                if      (pos == 0)  webView.loadUrl("https://www.youtube.com/");
                else if (pos == 1)  webView.loadUrl("https://www.naver.com/");
                else if (pos == 2)  webView.loadUrl("https://www.daum.net/");
                else if (pos == 3)  webView.loadUrl("http://afreecatv.com/");
                else {
                    final EditText editText = new EditText(mContext);
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(mContext);
                    builder1.setTitle("URL을 입력하세요.");
                    builder1.setView(editText);
                    builder1.setPositiveButton("Enter", (dialogInterface, i) -> webView.loadUrl(editText.getText().toString()));
                    builder1.setNegativeButton("Cancel", null);
                    AlertDialog alertDialog = builder1.create();
                    alertDialog.show();
                }
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        });

        logo.setOnClickListener(view -> btnLayout.setVisibility(View.INVISIBLE));

        logo.setOnLongClickListener(view -> {
            final EditText editText = new EditText(mContext);
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle("악성 문자열을 바꿀 텍스트를 입력해주세요.");
            builder.setView(editText);
            builder.setPositiveButton("Enter", (dialogInterface, i) -> {
                destText = editText.getText().toString();
                Toast.makeText(mContext, "변환 성공! 이제 \"" + destText + "\"으로 변환됩니다.", Toast.LENGTH_SHORT).show();
            });
            builder.setNegativeButton("Cancel", null);
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            return false;
        });

        btn_exit.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage("종료하시겠습니까?");
            builder.setPositiveButton("OK", (dialog, id) -> {
                stopTimerService();
                finish();
            });

            builder.setNegativeButton("Cancel", (dialog, id) -> {
            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        });

        detector = new GestureDetector(this, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                // Log.d("display", "onDown");
                tempTime    = SystemClock.uptimeMillis();
                oldY        = e.getY() + webView.getScrollY();
                return true;
            }

            @Override
            public void onShowPress(MotionEvent e) {
                // Log.d("display", "onShowPress");
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // Log.d("display", "onSingleTapUp");
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // Log.d("display", "onScroll");
                webView.scrollTo(webView.getScrollX(), (int) (oldY - e2.getY()));
                tempY = webView.getScrollY();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // Log.d("display", "onLongPress");
                webView.scrollTo(webView.getScrollX(), (int) (oldY - e.getY()));
                tempY = webView.getScrollY();
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // Log.d("display", "onFling");
                webView.scrollTo(webView.getScrollX(), (int) (oldY - e2.getY()));
                return true;
            }
        });

        webView.setOnTouchListener((view, event) -> {
            detector.onTouchEvent(event);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dY = view.getY() - event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    // if (dY + event.getRawY() <= 0)  btnLayout.animate().alpha(0.0f);
                    break;
                case MotionEvent.ACTION_UP:
                    if (dY + event.getRawY() <= 0)  {
                        btnLayout.animate().alpha(0.0f);
                        btnLayout.setVisibility(View.INVISIBLE);
                    }
                    else {
                        btnLayout.setVisibility(View.VISIBLE);
                        btnLayout.animate().alpha(1.0f);
                    }
                    break;
                default:
                    return false;
            }
            return false;
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100) {
                    TT = new TimerTask() {
                        @Override
                        public void run() {
                            webView.post(() -> webView.evaluateJavascript("javascript:document.body.innerText", value -> {
                                String[] temp = value.split("(\\\\n)");

                                /* 현재 페이지의 내용이 계속 같으면 악플검사를 하지 않음. */
                                if (ScreenText != temp) {
                                    /* 페이지 내용이 달라졌는데 기존에서 추가만 된 경우. */
                                    if (Arrays.toString(temp).contains(Arrays.toString(ScreenText))) {
                                        OrigLen = ScreenText.length;
                                        ScreenTextOrig = ScreenText;
                                        ScreenText = new String[temp.length - OrigLen];
                                        System.arraycopy(temp, OrigLen, ScreenText, 0, ScreenText.length);
                                        containScreenText = true;

                                        Message msg = handler.obtainMessage();
                                        handler.handleMessage(msg);
                                    } else {
                                        /* 페이지 내용이 달라졌는데 아예 처음부터 싹 달라졌을 경우. */
                                        ScreenText = temp;
                                        Message msg = handler.obtainMessage();
                                        handler.handleMessage(msg);
                                    }
                                }
                            }));
                        }
                    };
                    timer.schedule(TT, 500, 5000);
                }
            }
        });
    }

    public void stopTimerService() {
        while (timer != null && TT != null) {
            timer.cancel();
            timer = null;
            if (TT != null) TT = null;
        }
    }

    @Override
    public void onBackPressed() {
        webView.goBack();
    }

    @Override
    protected void onDestroy() {
        if (lite != null) {
            lite.close();
            lite = null;
        }
        super.onDestroy();
        System.exit(1);
    }

    private static class WebViewClientClass extends WebViewClient {
        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
    }

    // 문자 인식 및 결과 출력
    @SuppressLint("WrongConstant")
    public void processToText() {
        if(ScreenText != null) {
            /* tflite 선언 및 실행  (output[0][0] : 긍정, output[0][1] : 부정  ????) */
            ArrayList<String> BadComments = new ArrayList<>();
            for (String s : ScreenText) {
                if (s.equals("") || s.equals("\"")) continue;
                if (lite != null) {
                    float[][] input = tokenizeInputText(s);
                    try {
                        lite.run(input, output);
                        if (output[0][1] > 0.45) {
                            BadComments.add(s);
                            // Log.d("display", s + " >> " + Arrays.toString(output[0]));
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            /* 페이지가 추가만 된 경우 따로 기억해놓기 위해 기존 페이지 내용과 추가된 내용을 합쳐서 변수에 저장함. */
            if (containScreenText) {
                List<String> stringListOrig = new ArrayList<>(Arrays.asList(ScreenTextOrig));
                Collections.addAll(stringListOrig, ScreenText);
                ScreenText = stringListOrig.toArray(new String[0]);
                // Log.d("display", Arrays.toString(ScreenText));
                containScreenText = false;
            }

            /* 선별된 악플들을 비속어가 들어있는 것만 변환하는 작업 */
            for (String string : BadComments) {
                for (String cuss : cussList.cussList) {
                    if (string.contains(cuss)) {
                        if (string.contains("\"")) string = string.replaceAll("\"", "\\\"");
                        // Log.d("display", "BadComments : " + string);
                        final String finalString = string;
                        webView.post(() -> webView.evaluateJavascript("new function(){\n" +
                                "\tvar chooseText = function(parent) {\n" +
                                "\t\tif(parent.childElementCount == 0) {\n" +
                                "\t\t\tif(parent.textContent.indexOf(\""+ finalString +"\") >= 0){\n" +
                                "\t\t\t\tparent.textContent = \"" + destText + "\";\n" +
                                "\t\t\t}\n" +
                                "\t\t} else {\n" +
                                "\t\t\tfor(var i=0; i<parent.childElementCount; i++) {\n" +
                                "\t\t\t\tchooseText(parent.children[i]);\n" +
                                "\t\t\t}\n" +
                                "\t\t}\n" +
                                "\t};\n" +
                                "\tvar Inner = document.querySelectorAll('div');\n" +
                                "\tfor(var i=0; i<Inner.length;i++) {\n" +
                                "\t\tchooseText(Inner[i]);\n" +
                                "\t}\n" +
                                "}", null));
                        break;
                    }
                }
            }
        }
    }

    /* tflite 관련 함수 getTfliteInterpreter, loadModelFile, tokenizeInputText */
    private void getTfliteInterpreter() {
        try {
            //noinspection deprecation
            lite = new Interpreter(loadModelFile(MainActivity.this, "converted_model.tflite"));
        }
        catch (Exception e) { e.printStackTrace(); }
    }

    public MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor  = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream         = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel             = inputStream.getChannel();
        long startOffset                    = fileDescriptor.getStartOffset();
        long declaredLength                 = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    float[][] tokenizeInputText(String text) {
        String[] array  = text.split(SIMPLE_SPACE_OR_PUNCTUATION);
        float[] tmp     = new float[array.length];
        int index       = 0;
        boolean flag    = false;

        for (String word : array) {
            for (String cuss : cussList.cussList) {
                if (word.contains(cuss)) {
                    tmp[index] = 0.0f;
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                tmp[index++] = 1.0f;
            }
        }
        return new float[][]{tmp};
    }
}