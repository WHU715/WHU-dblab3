package drz.oddb;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.MarkerOptions;
import com.amap.api.maps2d.model.PolylineOptions;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.ServiceSettings;
import com.amap.api.services.geocoder.GeocodeSearch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import drz.oddb.Memory.TupleList;
import drz.oddb.Transaction.TransAction;
import drz.oddb.parse.ParseException;
import drz.oddb.parse.parse;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MapActivity extends AppCompatActivity {
    private AMap aMap;
    private MapView mapView;
    private GeocodeSearch geocoderSearch;

    private List<LatLng> list;
    private List<LatLng> list1;


    TransAction trans = new TransAction(this);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map);

        //获取地图控件引用
        mapView = (MapView) findViewById(R.id.map);
        //创建地图
        mapView.onCreate(savedInstanceState);



        try {
            init();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void init() throws JSONException, ParseException, IOException, InterruptedException {
        if (aMap == null) {
            aMap = mapView.getMap();
            try {
                geocoderSearch = new GeocodeSearch(this);
            } catch (AMapException e) {
                e.printStackTrace();
            }
            setUpMap();
        }
    }

    private void setUpMap() throws JSONException, ParseException, IOException, InterruptedException {


        //获取个人相关信息
        List<Object> information_personal = showListPersonal();
        List<LatLng> list_personal = (List<LatLng>) information_personal.get(0);
        List<Double> list_personal_x_double = (List<Double>) information_personal.get(1);
        List<Double> list_personal_y_double = (List<Double>) information_personal.get(2);

        //获取新冠疫情足迹点相关信息
        List<Object> information_covid19 = showListCovid19();
        List<LatLng> list_covid19 = (List<LatLng>) information_covid19.get(0);
        List<Double> list_covid19_x_double = (List<Double>) information_covid19.get(1);
        List<Double> list_covid19_y_double = (List<Double>) information_covid19.get(2);


        //personal_track_status表示每个个人足迹点是否为危险足迹点
        List<Boolean> personal_track_status = new ArrayList<>();

        for(int i = 0; i < list_personal.size(); i += 1) {
            Boolean status = Boolean.FALSE;
            for(int j = 0; j < list_covid19.size(); j += 1) {
                double x_minus = Math.abs(list_personal_x_double.get(i)-list_covid19_x_double.get(j));
                double y_minus = Math.abs(list_personal_y_double.get(i)-list_covid19_y_double.get(j));
                if (x_minus<0.01&&y_minus<0.01){
                    status = Boolean.TRUE;
                }
            }
            personal_track_status.add(status);
        }
        System.out.println(personal_track_status);

        //将地图视角聚焦到患者轨迹附近
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(list_covid19.get(0), 15));


        List<String> all_poi =new ArrayList<>();

        //此处模拟新冠患者历史足迹点文本信息，由政府发布，详细地理位置
        all_poi.add("珞喻路辅路与广八路交叉口东南260米 致瑞纯剪");
        all_poi.add("珞瑜路39号武汉大学科学孵化器大楼裙楼2楼F212 黄蜀郎鸡公煲");
        all_poi.add("街道口银泰创意城B1001(街道口地铁站出入口旁) 宫拉拉蒜香炸鸡(银泰创意城店)");
        all_poi.add("珞狮南路未来城购物公园(街道口地铁站L口旁) 江城回味鸭血粉丝汤");

        //显示新冠患者足迹点，此信息由政府发布
        for(int i = 0; i < list_covid19.size(); i += 1) {
//            System.out.println(list_covid19.get(i));
            aMap.addMarker(new MarkerOptions()
                    .position(list_covid19.get(i))
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.covid19_mark))
                    .title(all_poi.get(i))

            );
        }


        //显示个人轨迹线路集合
        aMap.addPolyline((new PolylineOptions())
                //集合数据
                 .addAll(list_personal)
                //线的宽度
                .width(15).setDottedLine(false).geodesic(true)
                //颜色
                .color(Color.argb(100,0,0,255))
        );

        //显示个人经过的轨迹点
        for(int i = 0; i < list_personal.size(); i += 1) {
            if(personal_track_status.get(i)==Boolean.TRUE){
                aMap.addMarker(new MarkerOptions()
                        .position(list_personal.get(i))
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.personal_mark))
                        .title("疑似与新冠患者时空伴随轨迹点")
                );
            }
            else{
                aMap.addMarker(new MarkerOptions()
                        .position(list_personal.get(i))
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.personal_mark_safe))
                        .title("安全轨迹点")
                );
            }
        }



    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //销毁地图
        mapView.onDestroy();
    }
    @Override
    protected void onResume() {
        super.onResume();
        //重新绘制加载地图
        mapView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        //暂停地图的绘制
        mapView.onPause();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //回调MapView的onSaveInstanceState，保存地图当前的状态
        mapView.onSaveInstanceState(outState);
    }


    /***
     *政府发布新冠患者轨迹点获取
     */
    private List<Object> showListCovid19() throws ParseException, IOException, JSONException, InterruptedException {
        List<Object> result = new ArrayList<Object>();
        List<LatLng> points = new ArrayList<LatLng>();

        List<Double> points_x_double = new ArrayList<Double>();
        List<Double> points_y_double = new ArrayList<Double>();

        List<String> all_poi =new ArrayList<>();

        //此处模拟新冠患者历史足迹点文本信息，由政府发布，详细地理位置
        all_poi.add("珞喻路辅路与广八路交叉口东南260米致瑞纯剪");
        all_poi.add("珞瑜路39号武汉大学科学孵化器大楼裙楼2楼F212黄蜀郎鸡公煲");
        all_poi.add("街道口银泰创意城B1001(街道口地铁站出入口旁)宫拉拉蒜香炸鸡(银泰创意城店)");
        all_poi.add("珞狮南路未来城购物公园(街道口地铁站L口旁)江城回味鸭血粉丝汤");

        ServiceSettings.updatePrivacyShow(this, true, true);
        ServiceSettings.updatePrivacyAgree(this,true);

        //根据POI名称调高德API查询经纬度信息
        for(int i = 0; i < all_poi.size(); i += 1) {
            String poi = all_poi.get(i);
            OkHttpClient httpClient = new OkHttpClient();
            String url = "https://restapi.amap.com/v3/geocode/geo?key=9ed1943afd63405b35e24257448ae9b1&address="+poi+"&city=武汉";
            Request getRequest = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            Call call = httpClient.newCall(getRequest);
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    String responseString =null;
                    try {
                        //同步请求，要放到子线程执行
                        Response response = call.execute();
                        assert response.body() != null;
                        String res_str = response.body().string();
                        System.out.println("okHttpGet run: response:"+res_str);
                        JSONObject jsonObj = new JSONObject(res_str);
                        JSONArray geocodes_array = (JSONArray) jsonObj.get("geocodes");
                        JSONObject res_geocode = geocodes_array.getJSONObject(0);
                        String location = (String) res_geocode.get("location");

                        String[] location_x_y = location.split(",");
                        String x  = location_x_y[1];
                        String y  = location_x_y[0];
                        System.out.println("此POI对应的经纬度 location"+location);
                        System.out.println("此POI对应的纬度"+x);
                        System.out.println("此POI对应的经度"+y);
                        double x_double = Double.parseDouble(x);
                        double y_double = Double.parseDouble(y);
                        points.add(new LatLng(x_double, y_double));
                        points_x_double.add(x_double);
                        points_y_double.add(y_double);

                    } catch (IOException | JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.start();
        }
        while(points.size()<all_poi.size()){
//            System.out.println("等待http请求");
        }
        result.add(points);
        result.add(points_x_double);
        result.add(points_y_double);
        return result;
    }


    /***
     *个人轨迹点集合
     */
    private List<Object> showListPersonal() throws ParseException, IOException, JSONException, InterruptedException {
        List<Object> result = new ArrayList<Object>();

        //point是所有个人轨迹点集合
        List<LatLng> points = new ArrayList<LatLng>();

        List<Double> points_x_double = new ArrayList<Double>();
        List<Double> points_y_double = new ArrayList<Double>();

        //all_poi_id是union出的结果的POI
        List<String> all_poi_id =new ArrayList<>();

        //用trans.DirectSelect() 查询所有UNION好的数据（在union_track_class），存到 all_poi_id
        String s="SELECT lon AS x FROM utrack WHERE flag=1;";

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(s.getBytes());
        parse p = new parse(byteArrayInputStream);
        String[] aa = p.Run();
        TupleList tpl = new TupleList();
        tpl = trans.DirectSelectPublic(aa);
        System.out.println(tpl.tuplenum);
        for (int i = 0; i < tpl.tuplenum; i += 1) {
            String y = (String) tpl.tuplelist.get(i).tuple[0];
            String x = (String) tpl.tuplelist.get(i).tuple[1];

            int y_int = Integer.parseInt(y);
            int x_int = Integer.parseInt(x);

            double y_double = (double) y_int /100000;//经度
            double x_double = (double) x_int /1000000;//纬度

            System.out.println("经度"+y_double);
            System.out.println("纬度"+x_double);
            //先加纬度、再加经度
            points.add(new LatLng(x_double, y_double));
            points_x_double.add(x_double);
            points_y_double.add(y_double);

        }
        result.add(points);
        result.add(points_x_double);
        result.add(points_y_double);
        return result;
    }

    private static final String X36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static int BASE = 36;
    private static HashMap<Integer, Character> tenToThirtysix = createMapTenToThirtysix();

    private static HashMap<Integer, Character> createMapTenToThirtysix() {
        HashMap<Integer, Character> map = new HashMap<Integer, Character>();
        for (int i = 0; i < X36.length(); i++) {
            // 0--0,... ..., 35 -- Z的对应存放进去
            map.put(i, X36.charAt(i));
        }
        return map;
    }

    public static String DeciamlToThirtySix(int iSrc) {
        String result = "";
        int key;
        int value;

        key = iSrc / BASE;
        value = iSrc - key * BASE;
        if (key != 0) {
            result = result + DeciamlToThirtySix(key);
        }

        result = result + tenToThirtysix.get(value).toString();

        return result;
    }

//    /***
//     *经纬度集合
//     */
//    private List<LatLng> showListLat() throws ParseException, IOException, JSONException, InterruptedException {
//        List<LatLng> points = new ArrayList<LatLng>();
//
//        //用trans.DirectSelect() 查询所有UNION好的数据（在union_track_class），存到 coords中
////        String s="SELECT x AS long,y AS lat FROM union_track_class WHERE flag=1";
////        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(s.getBytes());
////        parse p = new parse(byteArrayInputStream);
////        String[] aa = p.Run();
//
//        TupleList tpl = new TupleList();
//        List<String> all_poi =new ArrayList<>();
////        tpl = trans.DirectSelect(aa);
////        for (int i = 0; i < tpl.tuplenum; i += 1) {
////            all_poi.add((String) tpl.tuplelist.get(i).tuple[0]);
////        }
//
//        for (int i = 0; i < 2; i += 1) {
//            all_poi.add("武汉大学");
//        }
//        //all_poi是union出的结果的POI
//        ServiceSettings.updatePrivacyShow(this, true, true);
//        ServiceSettings.updatePrivacyAgree(this,true);
//
//        //根据POI名称调高德API查询经纬度信息
//        for(int i = 0; i < all_poi.size(); i += 1) {
//
//            String poi = all_poi.get(i);
//            OkHttpClient httpClient = new OkHttpClient();
//
//            String url = "https://restapi.amap.com/v3/geocode/geo?key=9ed1943afd63405b35e24257448ae9b1&address="+poi+"&city=武汉";
//            Request getRequest = new Request.Builder()
//                    .url(url)
//                    .get()
//                    .build();
//
//            Call call = httpClient.newCall(getRequest);
//
//            final String[] response_result = {null};
//            Thread thread = new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    String responseString =null;
//                    try {
//                        //同步请求，要放到子线程执行
//                        Response response = call.execute();
//                        assert response.body() != null;
//                        String res_str = response.body().string();
//                        System.out.println("okHttpGet run: response:"+res_str);
//                        JSONObject jsonObj = new JSONObject(res_str);
//                        JSONArray geocodes_array = (JSONArray) jsonObj.get("geocodes");
//                        JSONObject res_geocode = geocodes_array.getJSONObject(0);
//                        String location = (String) res_geocode.get("location");
//
//                        String[] location_x_y = location.split(",");
//                        String x  = location_x_y[1];
//                        String y  = location_x_y[0];
////                        String x  = "34.224944";
////                        String y  = "117.202596";
//
//                        System.out.println("此POI对应的经纬度 location"+location);
//                        System.out.println("此POI对应的纬度"+x);
//                        System.out.println("此POI对应的经度"+y);
//                        double x_float = Double.parseDouble(x);
//                        double y_float = Double.parseDouble(y);
//                        points.add(new LatLng(x_float, y_float));
//
//                    } catch (IOException | JSONException e) {
//                        e.printStackTrace();
//                    }
//                }
//            });
//            thread.start();
//        }
////        for (int i = 0; i < coords.length; i += 2) {
////            points.add(new LatLng(coords[i+1], coords[i]));
////        }
//        while(points.size()<all_poi.size()){
////            System.out.println("等待http请求");
//        }
//
//        return points;
//    }



    private double[] coords = {
            117.202596,34.224944,
            117.202836,34.213434,
            117.203934,34.210573,
            117.215414,34.212578,
            117.214921,34.206988,
            117.219641,34.211052,
            117.222838,34.210754
    };

    private List<LatLng> showListLat1(){
        List<LatLng> points = new ArrayList<LatLng>();
        for (int i = 0; i < coords1.length; i += 2) {
            points.add(new LatLng(coords1[i+1], coords1[i]));
        }
        return points;
    }

    private double[] coords1 = {
            117.222838,34.210754,
            117.225456,34.218526,
            117.209835,34.215013,
    };

}
