package drz.oddb;

import android.content.DialogInterface;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.amap.api.maps2d.MapView;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import drz.oddb.Transaction.TransAction;
import drz.oddb.parse.*;

public class MainActivity extends AppCompatActivity {
    //查询输入框

    private EditText editText;

    private TextView text_view;
    TransAction trans = new TransAction(this);
    TransAction trans_empty = new TransAction(this);

    Intent music = null;
    boolean is_initiated = false;

    private static final String BAIDU = "11432947,30556104,"
            + "11433613,30551480,"
            + "11433619,30545585,"
            + "11434408,30534081,"
            + "11435562,30533517,"
            + "11435657,30526436";
    private static final String GAODE = "11435940,30538595,"
            + "11435657,30526436,"
            + "11435028,30528870,"
            + "11434408,30534081,"
            + "11432681,30535148,"
            + "11432602,30530731";
    private static final String GOOGLE = "11435099,30547442,"
            + "11435163,30546370,"
            + "11435430,30542945,"
            + "11435511,30539785,"
            + "11435940,30538595,"
            + "11436655,30537521,"
            + "11435657,30526436";
    private static final String[] APP_NAMES = new String[] {"track1", "track2", "track3"};
    private static final String[] DATA = new String[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        music = new Intent(MainActivity.this,MusicServer.class);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //播放BGM
        //startService(music);

        //查询按钮
        Button button = findViewById(R.id.button);
        editText = findViewById(R.id.edit_text);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //onStop();
                //trans.Test();
                trans.query(editText.getText().toString());
            }
        });

        //退出按钮
        Button exit_button = findViewById(R.id.exit_button);
        exit_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showexitdialog(v);
                stopService(music);
            }
        });

        //展示按钮
        Button show_button = findViewById(R.id.showbutton);
        show_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //onStop();
                trans.PrintTab();
            }
        });

        //地图按钮
        Button button_map = findViewById(R.id.button_map);
        button_map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(!is_initiated){
                    try {
                        mapGenerate();
                        is_initiated=Boolean.TRUE;


                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                trans.SaveAll();
                onStop();
                Intent intent = new Intent(MainActivity.this,MapActivity.class);
                startActivity(intent);
            }
        });


    }

    protected void onStop(){
        Intent intent = new Intent(MainActivity.this,MusicServer.class);
        stopService(intent);
        super.onStop();
        Log.e("main", "...onstop");
    }

    protected void onStart(){
        super.onStart();
        startService(this.music);
        Log.e("main","...onstart");
    }

    //点击exit_button退出程序
    public void showexitdialog(View v){
        //定义一个新对话框对象
        AlertDialog.Builder exit_dialog = new AlertDialog.Builder(this);
        //设置对话框提示内容
        exit_dialog.setMessage("Do you want to save it before exiting the program?");
        //定义对话框两个按钮及接受事件
        exit_dialog.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //保存
                trans.SaveAll();
                //退出
                android.os.Process.killProcess(android.os.Process.myPid());

            }
        });
        exit_dialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                trans_empty.SaveAll();
                //退出
                android.os.Process.killProcess(android.os.Process.myPid());

            }
        });
        //创建并显示对话框
        AlertDialog exit_dialog0 = exit_dialog.create();
        exit_dialog0.show();

    }

    public void mapGenerate() throws FileNotFoundException {
//		String dir_path = "D:\\default\\桌面\\trace_data";
//
//	    File files = new File(dir_path);
//	    File[] file_list = files.listFiles();
//	    String[] file_name_list = files.list();
//	    ArrayList<String> app_names = new ArrayList<String>();
//
//	    for(int i=0;i<file_list.length;i++) {
//	    	String file_name = file_name_list[i];
//	    	// 只有.txt文件才会被识别为轨迹数据
//	    	if(file_list[i].isFile()&&file_name.substring(file_name.length()-4).equals(".txt")) {
//	    		// 获取app名称
//	    		String app_name = file_name.substring(0, file_name.length()-4);
//	    		app_names.add(app_name);
//
//	    		// 创建与app名同名class表
//	    		String[] attr_name_list = appTableCreate(app_name);
//	    		attr_name_lists.add(attr_name_list);
//
//	    		// 读取规矩数据并插入
//	    		String file_path = dir_path+"\\"+file_name;
//	    		Scanner sc = new Scanner(new FileReader(file_path));
//	    		while (sc.hasNextLine()) {
//	    			String[] cur_tuple = sc.nextLine().split(",");
//	    			appTupleCreate(app_name, cur_tuple[0], cur_tuple[1], cur_tuple[2]);
//	    		}
//
//	    		sc.close();
//	    	}
//	    }

        ArrayList<String[]> attr_name_lists = new ArrayList<String[]>();

        DATA[0] = BAIDU;
        DATA[1] = GAODE;
        DATA[2] = GOOGLE;

        for(int i=0;i<3;i++) {
            String[] attr_name_list = trans.appTableCreate(APP_NAMES[i]);
            attr_name_lists.add(attr_name_list);
            String[] cur_data = DATA[i].split(",");
            for(int j=0;j<cur_data.length>>>1;j++) {
                trans.appTupleCreate(APP_NAMES[i], cur_data[0+j*2], cur_data[1+j*2]);
            }

        }

        // 创建app的union类
        trans.appUnionCreate(APP_NAMES, attr_name_lists);
        trans.SaveAll();
        return;
    }


}
