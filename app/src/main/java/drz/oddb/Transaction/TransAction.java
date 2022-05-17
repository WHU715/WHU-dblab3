package drz.oddb.Transaction;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import drz.oddb.Log.*;
import drz.oddb.Memory.*;


import drz.oddb.show.PrintResult;
import drz.oddb.show.ShowTable;
import drz.oddb.Transaction.SystemTable.*;

import drz.oddb.parse.*;

public class TransAction {
    public TransAction(Context context) {
        this.context = context;
        RedoRest();
    }

    Context context;
    public MemManage mem = new MemManage();

    public ObjectTable topt = mem.loadObjectTable();
    public ClassTable classt = mem.loadClassTable();
    public DeputyTable deputyt = mem.loadDeputyTable();
    public BiPointerTable biPointerT = mem.loadBiPointerTable();
    public SwitchingTable switchingT = mem.loadSwitchingTable();

    LogManage log = new LogManage(this);

    public void SaveAll( )
    {
        mem.saveObjectTable(topt);
        mem.saveClassTable(classt);
        mem.saveDeputyTable(deputyt);
        mem.saveBiPointerTable(biPointerT);
        mem.saveSwitchingTable(switchingT);
        mem.saveLog(log.LogT);
        while(!mem.flush());
        while(!mem.setLogCheck(log.LogT.logID));
        mem.setCheckPoint(log.LogT.logID);//成功退出,所以新的事务块一定全部执行
    }

    public void Test(){
        TupleList tpl = new TupleList();
        Tuple t1 = new Tuple();
        t1.tupleHeader = 5;
        t1.tuple = new Object[t1.tupleHeader];
        t1.tuple[0] = "a";
        t1.tuple[1] = 1;
        t1.tuple[2] = "b";
        t1.tuple[3] = 3;
        t1.tuple[4] = "e";
        Tuple t2 = new Tuple();
        t2.tupleHeader = 5;
        t2.tuple = new Object[t2.tupleHeader];
        t2.tuple[0] = "d";
        t2.tuple[1] = 2;
        t2.tuple[2] = "e";
        t2.tuple[3] = 2;
        t2.tuple[4] = "v";

        tpl.addTuple(t1);
        tpl.addTuple(t2);
        String[] attrname = {"attr2","attr1","attr3","attr5","attr4"};
        int[] attrid = {1,0,2,4,3};
        String[]attrtype = {"int","char","char","char","int"};

        PrintSelectResult(tpl,attrname,attrid,attrtype);

        int[] a = InsertTuple(t1);
        Tuple t3 = GetTuple(a[0],a[1]);
        int[] b = InsertTuple(t2);
        Tuple t4 = GetTuple(b[0],b[1]);
        System.out.println(t3);
    }

    private boolean RedoRest(){//redo
        LogTable redo;
        if((redo=log.GetReDo())!=null) {
            int redonum = redo.logTable.size();   //先把redo指令加前面
            for (int i = 0; i < redonum; i++) {
                String s = redo.logTable.get(i).str;

                log.WriteLog(s);
                query(s);
            }
        }else{
            return false;
        }
        return true;
    }

    public String query(String s) {

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(s.getBytes());
        parse p = new parse(byteArrayInputStream);
        try {
            String[] aa = p.Run();

            switch (Integer.parseInt(aa[0])) {
                case parse.OPT_CREATE_ORIGINCLASS:
                    log.WriteLog(s);
                    CreateOriginClass(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("创建成功").setPositiveButton("确定",null).show();
                    break;
                case parse.OPT_CREATE_SELECTDEPUTY:
                    log.WriteLog(s);
                    CreateSelectDeputy(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("创建成功").setPositiveButton("确定",null).show();
                    break;
                case parse.OPT_CREATE_UNIONDEPUTY:
                    log.WriteLog(s);
                    CreateUnionDeputy(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("创建成功").setPositiveButton("确定",null).show();
                    break;
                case parse.OPT_DROP:
                    log.WriteLog(s);
                    Drop(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("删除成功").setPositiveButton("确定",null).show();
                    break;
                case parse.OPT_INSERT:
                    log.WriteLog(s);
                    Insert(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("插入成功").setPositiveButton("确定",null).show();
                    break;
                case parse.OPT_DELETE:
                    log.WriteLog(s);
                    Delete(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("删除成功").setPositiveButton("确定",null).show();
                    break;
                case parse.OPT_SELECT_DERECTSELECT:
                    DirectSelect(aa);
                    break;
                case parse.OPT_SELECT_INDERECTSELECT:
                    InDirectSelect(aa);
                    break;
                case parse.OPT_CREATE_UPDATE:
                    log.WriteLog(s);
                    Update(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("更新成功").setPositiveButton("确定",null).show();
                default:
                    break;

            }
        } catch (ParseException e) {

            e.printStackTrace();
        }

        return s;

    }



    //CREATE CLASS dZ123 (nB1 int,nB2 char) ;
    //1,2,dZ123,nB1,int,nB2,char
    private void CreateOriginClass(String[] p) {
        String classname = p[2];
        int count = Integer.parseInt(p[1]);
        classt.maxid++;
        int classid = classt.maxid;
        for (int i = 0; i < count; i++) {
            classt.classTable.add(new ClassTableItem(classname, classid, count,i,p[2 * i + 3], p[2 * i + 4],"ori"));
        }
    }

    //CREATE SELECTDEPUTY aa SELECT  b1+2 AS c1,b2 AS c2,b3 AS c3 FROM  bb WHERE t1="1" ;
    //2,3,aa,b1,1,2,c1,b2,0,0,c2,b3,0,0,c3,bb,t1,=,"1"
    //0 1 2  3  4 5 6  7  8 9 10 11 121314 15 16 17 18
    private void CreateSelectDeputy(String[] p) {
        int count = Integer.parseInt(p[1]);
        String classname = p[2];//代理类的名字
        String bedeputyname = p[4*count+3];//代理的类的名字
        classt.maxid++;
        int classid = classt.maxid;//代理类的id
        int bedeputyid = -1;//代理的类的id
        String[] attrname=new String[count];
        String[] bedeputyattrname=new String[count];
        int[] bedeputyattrid = new int[count];
        String[] attrtype=new String[count];
        int[] attrid=new int[count];
        for(int j = 0;j<count;j++){
            attrname[j] = p[4*j+6];
            attrid[j] = j;
            bedeputyattrname[j] = p[4*j+3];
        }

        String attrtype1;
        for (int i = 0; i < count; i++) {
            //对 选择代理类 的各属性逐一处理

            for (ClassTableItem item:classt.classTable) {
                if (item.classname.equals(bedeputyname)&&item.attrname.equals(p[3+4*i])) {
                    //在系统class表中找到了被代理类的item，且找到的被代理类的属性对应与待添加的代理类的属性
                    bedeputyid = item.classid;
                    bedeputyattrid[i] = item.attrid;
                    //在系统表中插入代理类及其属性信息
                    //类名、类id、类的属性个数、属性id、属性名、属性类型、类的类型（'de'表示这是一个代理类）
                    classt.classTable.add(new ClassTableItem(classname, classid, count,attrid[i],attrname[i], item.attrtype,"de"));

                    //在switching表中插入切换规则
                    //被代理类的属性名、当前要进行添加的代理类的属性（注意这两者是对应的）
                    //0表示无特定规则，直接对应；1表示有规则，如加某个特定值
                    if(Integer.parseInt(p[4+4*i]) == 1){
                        switchingT.switchingTable.add(new SwitchingTableItem(item.attrname,attrname[i],p[5+4*i]));
                    }
                    if(Integer.parseInt(p[4+4*i])==0){
                        switchingT.switchingTable.add(new SwitchingTableItem(item.attrname,attrname[i],"0"));
                    }
                    break;
                }
            };
        }

        //以下con=[t1,=,"1"]
        String[] con =new String[3];
        con[0] = p[4 + 4 * count];
        con[1] = p[5 + 4 * count];
        con[2] = p[6 + 4 * count];
        //在deputyTable新增此规则
        //被代理类id、代理类id、代理的条件
        deputyt.deputyTable.add(new DeputyTableItem(bedeputyid,classid,con));


        TupleList tpl= new TupleList();

        //现在系统表classTable中找到该代理规则中涉及的属性id及属性类型
        int conid = 0;
        String contype  = null;
        for(ClassTableItem item3:classt.classTable){
            if(item3.attrname.equals(con[0])){
                conid = item3.attrid;
                contype = item3.attrtype;
                break;
            }
        }
        List<ObjectTableItem> obj = new ArrayList<>();
        //对于objectTable中的每个对象处理
        for(ObjectTableItem item2:topt.objectTable){

            //如果此待处理对象的对应的类 是 被代理类 也就是说：这个对象是源类的一个对象
            if(item2.classid ==bedeputyid){
                //找到该对象的元组
                Tuple tuple = GetTuple(item2.blockid,item2.offset);
                //判断是否满足代理规则
                //代理规则类型、待判断元组、代理规则id、代理规则可满足条件（如con=[t1,=,"1"]中的"1"）
                if(Condition(contype,tuple,conid,con[2])){

                    //新建一个元组ituple
                    Tuple ituple = new Tuple();
                    //ituple的tupleHeader表示这个元组共有几个属性
                    ituple.tupleHeader = count;
                    //ituple的tuple为一个Object类型数组，其中存的是所有属性的具体值
                    ituple.tuple = new Object[count];

                    //对于此满足代理规则的源类中的对象
                    //一个代理类属性一个代理类属性地处理
                    for(int o =0;o<count;o++){
                        //遇到代理类属性为需要switch的（也就是==1）
                        if(Integer.parseInt(p[4+4*o]) == 1){
                            //value是switch规则的待加项
                            int value = Integer.parseInt(p[5+4*o]);
                            //orivalue是这个对象的原始值（由于涉及到加减，所以要先解析成数字int）
                            int orivalue =Integer.parseInt((String)tuple.tuple[bedeputyattrid[o]]);
                            Object ob = value+orivalue;
                            ituple.tuple[o] = ob;
                        }
                        //遇到代理类属性为不需要switch的（也就是==0）
                        if(Integer.parseInt(p[4+4*o]) == 0){
                            //直接把这个位置的属性具体值置为原始值（可能是数字，也可能是其他的）
                            ituple.tuple[o] = tuple.tuple[bedeputyattrid[o]];
                        }
                    }

                    //把元组最大id+1，表示要新增一个对象了，这个对象属于代理类
                    topt.maxTupleId++;
                    int tupid = topt.maxTupleId;

                    int [] aa = InsertTuple(ituple);
                    //topt.objectTable.add(new ObjectTableItem(classid,tupid,aa[0],aa[1]));

                    //这个obj是之前定义的一个ObjectTableItem的数组，这里先把这个ObjectTableItem构造出来并存到这个数组中
                    //代理类id、对象id（属于这个代理类）、块id、块内偏移
                    obj.add(new ObjectTableItem(classid,tupid,aa[0],aa[1]));

                    //在双向指针表，新增一个BiPointerTableItem
                    //BiPointerTableItem中包括
                    //源类id、待处理对象的tupleid（这个对象是属于源类的）、代理类id、刚才新加的对象的tupleid（这个对象是属于代理类的）

                    biPointerT.biPointerTable.add(new BiPointerTableItem(bedeputyid,item2.tupleid,classid,tupid));

                }
            }
        }
        for(ObjectTableItem item6:obj) {
            topt.objectTable.add(item6);
        }
    }


    private void CreateUnionDeputy(String[] p) {
        int select_cnt = Integer.parseInt(p[1]);        // UNION的SELECT个数
        int attr_cnt = Integer.parseInt(p[2]);          // UNION的属性个数
        final int attr_block_size = 4;                       // 一个属性块在p中所占元素个数（恒为4）
        final int select_block_size = (attr_cnt + 1) << 2;   // 一个SELECT语句在p中所占元素个数

        // 获取 代理类 和 被代理的类 的基本信息
        classt.maxid++;
        int d_classid = classt.maxid;                                       // 代理类的id
        String d_classname = p[3];                                          // 代理类的名字
        int[] d_attrid = new int[attr_cnt];                                 // 代理类的属性ID
        String[] d_attrname = SafeGetAttrName(p, select_cnt, attr_cnt);     // 代理类的属性名
        String[] d_attrtype = new String[attr_cnt];                         // 代理属性类型

        int[] bd_classid = new int[select_cnt];                             // 被代理类的id
        String[] bd_classname = new String[select_cnt];                     // 被代理类的名字
        int[][] bd_attrid = new int[select_cnt][attr_cnt];                  // 被代理的类的属性ID
        String[][] bd_attrname = new String[select_cnt][attr_cnt];          // 被代理的类的属性名

        // 获取代理属性ID，就是生成一个递增序列
        for (int i = 0; i < attr_cnt; i++) {
            d_attrid[i] = i;
        }

        // 获取被代理类名
        for (int i = 0; i < select_cnt; i++) {
            bd_classname[i] = p[4 + attr_block_size * attr_cnt + i * select_block_size];
        }

        // 获取被代理属性名
        for (int i = 0; i < select_cnt; i++) {
            for (int j = 0; j < attr_cnt; j++) {
                bd_attrname[i][j] = p[4 + attr_block_size * j + select_block_size * i];
            }
        }

        // 调试用输出所有相关类
        // 代理类信息
        System.out.println("--------UNION_DEPUTY类--------");
        System.out.println("代理类ID：" + d_classid);
        System.out.println("代理类类名：" + d_classname);
        for (int i = 0; i < attr_cnt; i++) {
            System.out.println("代理类属性ID：" + d_attrid[i]);
            System.out.println("代理类属性名：" + d_attrname[i]);
        }
        System.out.println();

        // 被代理类信息
        System.out.println("-------UNION_BEDEPUTY类-------");
        for (int i = 0; i < select_cnt; i++) {
            System.out.println("被代理类类名：" + bd_classname[i]);
            System.out.println("被代理类属性表");
            for (int j = 0; j < attr_cnt; j++) {
                System.out.println("被代理类属性名：" + bd_attrname[i][j]);
            }
        }

        String attrtype1;
        for (int i = 0; i < select_cnt; i++) {
            for (int j = 0; j < attr_cnt; j++) {
                for (ClassTableItem item : classt.classTable) {
                    if (item.classname.equals(bd_classname[i]) && item.attrname.equals(bd_attrname[i][j])) {
                        bd_classid[i] = item.classid;
                        bd_attrid[i][j] = item.attrid;

                        /*
                         * 添加项到Class表中：代理类类名、代理类ID、代理属性数、代理属性ID、代理属性名、代理属性类型、类类型
                         *
                         * 部分参数说明
                         * 代理属性ID：每个代理类的代理属性都是从0开始排，attrid[i] = i
                         * 代理属性类型：只有"int" "char" 两种属性类型，UNION前需要检查
                         * 类类型：对于代理类，类类型就是代理类（de）；为了在执行功能时能区别不同的代理类（SELECT代理类、），
                         * 自行设计了更细致的代理类划分，UNION代理类的类类型为（de_union)
                         * */
                        if (i == 0) {
                            System.out.println("-------执行到添加class！-------");
                            System.out.println("添加代理类名：" + d_classname);
                            System.out.println("添加代理类ID：" + d_classid);
                            System.out.println("添加代理类代理属性数：" + attr_cnt);
                            System.out.println("添加代理类代理属性ID：" + d_attrid[j]);
                            System.out.println("添加代理类代理属性名：" + d_attrname[j]);
                            System.out.println("添加代理类代理属性类型：" + item.attrtype);
                            System.out.println("添加代理类类型：de_union");
                            classt.classTable.add(new ClassTableItem(d_classname, d_classid, attr_cnt,
                                    d_attrid[j], d_attrname[j], item.attrtype, "de_union"));
                        }
                        /*
                         * 添加代理规则相关到switch表中：被代理属性名、代理属性名、switch规则
                         *
                         * 部分参数说明
                         * switch规则：即SQL的SElECT语句中 要么属性原值（0），要么属性+某个值 的规则
                         *
                         * switch_rule_flag：switch规则标志位，用来区分没有规则和有规则的情况
                         * */


                        int switch_rule_flag = Integer.parseInt(p[5 + attr_block_size * j + select_block_size * i]);
                        String switch_rule = p[6 + attr_block_size * j + select_block_size * i];
                        System.out.println("-------执行到添加switch！-------");
                        System.out.println("添加被代理属性名：" + item.attrname);
                        System.out.println("添加代理属性名：" + d_attrname[j]);
                        System.out.println("switch规则标志符：" + switch_rule_flag);
                        System.out.println("switch规则：" + switch_rule);
                        if (switch_rule_flag == 1) {
                            switchingT.switchingTable.add(new SwitchingTableItem(item.attrname, d_attrname[j], switch_rule));
                        } else {
                            switchingT.switchingTable.add(new SwitchingTableItem(item.attrname, d_attrname[j], "0"));
                        }
                        break;
                    }
                }
            }
        }

        // 被代理类信息
        System.out.println("-------UNION_BEDEPUTY类-------");
        for (int i = 0; i < select_cnt; i++) {
            System.out.println("被代理类类名：" + bd_classname[i]);
            System.out.println("被代理类属性表");
            for (int j = 0; j < attr_cnt; j++) {
                System.out.println("被代理类属性名：" + bd_attrname[i][j]);
                System.out.println("被代理类属性ID：" + bd_attrid[i][j]);
            }
        }

        //代理规则的添加
        String[][] cons = new String[select_cnt][3];
        for (int i = 0; i < select_cnt; i++) {
            String[] con = new String[3];
            con[0] = p[5 + attr_cnt * attr_block_size + i * select_block_size];
            con[1] = p[6 + attr_cnt * attr_block_size + i * select_block_size];
            con[2] = p[7 + attr_cnt * attr_block_size + i * select_block_size];
            deputyt.deputyTable.add(new DeputyTableItem(bd_classid[i], d_classid, con));
            cons[i] = con;
        }

        List<ObjectTableItem> obj = new ArrayList<>();
        for (int i = 0; i < select_cnt; i++) {
            //先在系统表classTable中找到该代理规则中涉及的属性id及属性类型
            int conid = 0;
            String contype = null;
            //需要满足属性名相同，还要满足classid为 此被代理类id
            for (ClassTableItem item3 : classt.classTable) {
                if (item3.attrname.equals(cons[i][0]) && (item3.classid == bd_classid[i])) {
                    conid = item3.attrid;
                    contype = item3.attrtype;
                    break;
                }
            }

            //对于objectTable中的每个对象处理
            for (ObjectTableItem item2 : topt.objectTable) {

                //如果此待处理对象的对应的类 是 被代理类 也就是说：这个对象是源类的一个对象
                if (item2.classid == bd_classid[i]) {
                    //找到该对象的元组
                    Tuple tuple = GetTuple(item2.blockid, item2.offset);
                    //判断是否满足代理规则
                    //代理规则类型、待判断元组、代理规则的属性id、代理规则可满足条件（如con=[t1,=,"1"]中的"1"）
                    if (Condition(contype, tuple, conid, cons[i][2])) {

                        //新建一个元组ituple
                        Tuple ituple = new Tuple();
                        //ituple的tupleHeader表示这个元组共有几个属性
                        ituple.tupleHeader = attr_cnt;
                        //ituple的tuple为一个Object类型数组，其中存的是所有属性的具体值
                        ituple.tuple = new Object[attr_cnt];

                        //对于此满足代理规则的源类中的对象
                        //一个代理类属性一个代理类属性地处理
                        for (int o = 0; o < attr_cnt; o++) {
                            //遇到代理类属性为需要switch的（也就是==1）,相关：第i个select项的第o个属性
                            if (Integer.parseInt(p[5 + attr_block_size * o + select_block_size * i]) == 1) {
                                //value是switch规则的待加项
                                int value = Integer.parseInt(p[6 + attr_block_size * o + select_block_size * i]);
                                //orivalue是这个对象的原始值（由于涉及到加减，所以要先解析成数字int）
                                int orivalue = Integer.parseInt((String) tuple.tuple[bd_attrid[i][o]]);
                                Object ob = value + orivalue;
                                ituple.tuple[o] = ob;
                            }
                            //遇到代理类属性为不需要switch的（也就是==0）
                            if (Integer.parseInt(p[5 + attr_block_size * o + select_block_size * i]) == 0) {
                                //直接把这个位置的属性具体值置为原始值（可能是数字，也可能是其他的）
                                ituple.tuple[o] = tuple.tuple[bd_attrid[i][o]];
                            }
                        }
                        //表明要新增的这个元组是否已经存在
                        int tupid =0;
                        boolean d_obj_existed=Boolean.FALSE;
                        int exist_tupleid=0;

                        //遍历对象表，查看此待插入代理对象是否已经存在
                        for(ObjectTableItem obj_item:topt.objectTable){
                            if(obj_item.classid == d_classid){

                                //每遇到一个该代理类的对象，给一次机会，值暂时置为True
                                d_obj_existed=Boolean.TRUE;
                                Tuple tuple_obj_item = GetTuple(obj_item.blockid,obj_item.offset);
                                for (int j = 0;j<attr_cnt;j++){
                                    if (!ituple.tuple[j].equals(tuple_obj_item.tuple[j])){
                                        //只要遇到一个不等的属性值，tuple_existed值置为FALSE
                                        d_obj_existed=Boolean.FALSE;
                                        break;
                                    }
                                }
                                if(d_obj_existed){
                                    //如果所有属性值都相同了,直接返回这个已存在的tuple
                                    exist_tupleid = obj_item.tupleid;
                                    break;
                                }
                            }
                        }
                        //遍历待添加的表项，查看此待插入代理对象是否已经存在于待添加list
                        for(ObjectTableItem obj_item:obj){
                            if(obj_item.classid == d_classid){

                                //每遇到一个该代理类的对象，给一次机会，值暂时置为True
                                d_obj_existed=Boolean.TRUE;
                                Tuple tuple_obj_item = GetTuple(obj_item.blockid,obj_item.offset);
                                for (int j = 0;j<attr_cnt;j++){
                                    if (!ituple.tuple[j].equals(tuple_obj_item.tuple[j])){
                                        //只要遇到一个不等的属性值，tuple_existed值置为FALSE
                                        d_obj_existed=Boolean.FALSE;
                                        break;
                                    }
                                }
                                if(d_obj_existed){
                                    //如果所有属性值都相同了,直接返回这个已存在的tuple
                                    exist_tupleid = obj_item.tupleid;
                                    break;
                                }
                            }
                        }

                        if(d_obj_existed){
                            tupid=exist_tupleid;
                        }
                        else{
                            //把元组最大id+1，表示要新增一个对象了，这个对象属于代理类
                            topt.maxTupleId++;
                            tupid = topt.maxTupleId;
                            int [] aa = InsertTuple(ituple);
                            //这个obj是之前定义的一个ObjectTableItem的数组，这里先把这个ObjectTableItem构造出来并存到这个数组中
                            //代理类id、对象id（属于这个代理类）、块id、块内偏移
                            obj.add(new ObjectTableItem(d_classid,tupid,aa[0],aa[1]));
                        }
                        //在双向指针表，新增一个BiPointerTableItem
                        //BiPointerTableItem中包括
                        //源类id、待处理对象的tupleid（这个对象是属于源类的）、代理类id、刚才新加的对象的tupleid（这个对象是属于代理类的）
                        biPointerT.biPointerTable.add(new BiPointerTableItem(bd_classid[i], item2.tupleid, d_classid, tupid));

                    }
                }
            }
        }


        topt.objectTable.addAll(obj);
    }



    private String[] SafeGetAttrName(String[] p, int select_cnt, int attr_cnt){
        String[] attr_name = new String[attr_cnt];
        final int attr_block_size = 4;
        final int select_block_size = (attr_cnt+1) << 2;

        for(int i=0;i<attr_cnt;i++){
            attr_name[i] = p[7+i*4];
        }
        for(int i=0;i<select_cnt;i++){
            for(int j=0;j<attr_cnt;j++){
                if(!attr_name[j].equals(p[7+attr_block_size*j+select_block_size*i])){
                    throw new RuntimeException("代理属性名不统一！");
                }
            }
        }
        return attr_name;
    }
    //DROP CLASS asd;
    //3,asd
    private void Drop(String[]p){
        List<DeputyTableItem> dti;
        dti = Drop1(p);
        for(DeputyTableItem item:dti){
            deputyt.deputyTable.remove(item);
        }
    }

    private List<DeputyTableItem> Drop1(String[] p){
        //classname 是某个待删除的源类
        String classname = p[1];
        int classid = 0;
        //找到classid顺便 清除类表和switch表
        for (Iterator it1 = classt.classTable.iterator(); it1.hasNext();) {
            ClassTableItem item =(ClassTableItem) it1.next();
            if (item.classname.equals(classname) ){
                //在class系统表中找到此classname对应的item，注意一个class会对应多个这样的item
                classid = item.classid;
                for(Iterator it = switchingT.switchingTable.iterator(); it.hasNext();) {
                    //对于switchingTable的内容逐一扫描
                    SwitchingTableItem item2 =(SwitchingTableItem) it.next();
                    //在switchingTable中找到待删除类的某个属性；有两种，一个是此待删除类作为代理类，另一种是作为被代理类
                    if (item2.attr.equals( item.attrname)||item2.deputy .equals( item.attrname)){
                        //清除switchingTable的该项
                        it.remove();
                    }
                }
                //清除classTable的该项
                it1.remove();
            }
        }

        //清元组表同时清了双向指针表
        OandB ob2 = new OandB();
        for(ObjectTableItem item1:topt.objectTable){
            if(item1.classid == classid){
                OandB ob = DeletebyID(item1.tupleid);
                for(ObjectTableItem obj:ob.o){
                    ob2.o.add(obj);
                }
                for(BiPointerTableItem bip:ob.b){
                    ob2.b.add(bip);
                }
            }
        }
        for(ObjectTableItem obj:ob2.o){
            topt.objectTable.remove(obj);
        }
        for(BiPointerTableItem bip:ob2.b) {
            biPointerT.biPointerTable.remove(bip);
        }

        //清deputy
        List<DeputyTableItem> dti = new ArrayList<>();
        for(DeputyTableItem item3:deputyt.deputyTable){
            if(item3.deputyid == classid){
                if(!dti.contains(item3))
                    dti.add(item3);
            }
            if(item3.originid == classid){

                //待删除类作为源类，要清理其对应的代理类
                String[]s = p.clone();
                List<String> sname = new ArrayList<>();
                for(ClassTableItem item5: classt.classTable) {
                    if (item5.classid == item3.deputyid) {
                        //找到了这个待删除类对应的代理类，只有一个
                        sname.add(item5.classname);
                    }
                }
                for(String item4: sname){

                    s[1] = item4;
                    List<DeputyTableItem> dti2 = Drop1(s);
                    for(DeputyTableItem item8:dti2){
                        if(!dti.contains(item8))
                            dti.add(item8);
                    }

                }
                if(!dti.contains(item3))
                    dti.add(item3);
            }
        }
        return dti;

    }


    //INSERT INTO aa VALUES (1,2,"3");
    //4,3,aa,1,2,"3"
    //0 1 2  3 4  5
    private int Insert(String[] p){


        int count = Integer.parseInt(p[1]);
        for(int o =0;o<count+3;o++){
            p[o] = p[o].replace("\"","");
        }

        String classname = p[2];
        Object[] tuple_ = new Object[count];

        int classid = 0;

        for(ClassTableItem item:classt.classTable)
        {
            if(item.classname.equals(classname)){
                classid = item.classid;
            }
        }

        for(int j = 0;j<count;j++){
            tuple_[j] = p[j+3];
        }

        /////////////////////////////////////////////////////////
        ////////////////新增代码部分///////////////////////////////
        //如果这个tuple已经在该类中存在，则返回的是已经存在的tupleid
        boolean tuple_existed=Boolean.TRUE;
        int exist_tupleid=0;

        //遍历对象表，查看此待插入代理对象是否已经存在
        for(ObjectTableItem obj_item:topt.objectTable){
            if(obj_item.classid == classid){
                //每遇到一个该类的对象，给一次机会，值暂时置为True
                tuple_existed=Boolean.TRUE;
                Tuple tuple_obj_item = GetTuple(obj_item.blockid,obj_item.offset);
                for (int j = 0;j<count;j++){
                    if (!(tuple_[j].equals(tuple_obj_item.tuple[j]))){
                        //只要遇到一个不等的属性值，tuple_existed值置为FALSE
                        tuple_existed=Boolean.FALSE;
                        break;
                    }
                }
                if(tuple_existed){
                    //如果所有属性值都相同了,直接返回这个已存在的tupleid
                    exist_tupleid = obj_item.tupleid;
                    return exist_tupleid;

//                    break;
                }
            }
        }

        Tuple tuple = new Tuple(tuple_);
        tuple.tupleHeader=count;

        int tupleid =0;
        int[] a = InsertTuple(tuple);
        topt.maxTupleId++;
        tupleid = topt.maxTupleId;
        topt.objectTable.add(new ObjectTableItem(classid,tupleid,a[0],a[1]));

//        if(tuple_existed){
//
//            return exist_tupleid;
//        }
//        else{
//            int[] a = InsertTuple(tuple);
//            topt.maxTupleId++;
//            tupleid = topt.maxTupleId;
//            topt.objectTable.add(new ObjectTableItem(classid,tupleid,a[0],a[1]));
//        }

        /////////////////////////////////////////////////////////
        /////////////////////////////////////////////////////////
        //向代理类加元组

        for(DeputyTableItem item:deputyt.deputyTable){
            if(classid == item.originid){
                //该item是以待插入元组的类作为源类的代理表项

                //判断代理规则

                //在class表中找到类id为 待插入元组的类id ，属性名为代理规则中的属性
                String attrtype=null;
                int attrid=0;
                for(ClassTableItem item1:classt.classTable){
                    if(item1.classid == classid&&item1.attrname.equals(item.deputyrule[0])) {
                        attrtype = item1.attrtype;
                        attrid = item1.attrid;
                        break;
                    }
                }



                //如果满足此代理规则
                if(Condition(attrtype,tuple,attrid,item.deputyrule[2])){
                    System.out.println("满足代理规则，即将插入");
                    String[] ss= p.clone();
                    String s1 = null;

                    for(ClassTableItem item2:classt.classTable){
                        if(item2.classid == item.deputyid) {
                            s1 = item2.classname;
                            break;
                        }
                    }

                    //是否要插switch的值
                    //收集源类属性名和属性id
                    String[] attrname1 = new String[count];
                    int[] attrid1 = new int[count];
                    int k=0;
                    for(ClassTableItem item3 : classt.classTable){
                        if(item3.classid == classid){
                            attrname1[k] = item3.attrname;
                            attrid1[k] = item3.attrid;
                            k++;

                            if (k ==count)
                                break;
                        }
                    }

                    for (int l = 0;l<count;l++) {
                        //逐个属性考虑
                        for (SwitchingTableItem item4 : switchingT.switchingTable) {
                            if (item4.attr.equals(attrname1[l])){
                                //在switch表中找到了属性名为待处理的源类的属性名

                                for(ClassTableItem item8: classt.classTable){
                                    //在class表中找到该待处理的源类属性所对应的代理属性
                                    if(item8.attrname.equals(item4.deputy)&&Integer.parseInt(item4.rule)!=0){
                                        //且该代理属性对应的类id就是代理类的
                                        if(item8.classid==item.deputyid){
                                            int sw = Integer.parseInt(p[3+attrid1[l]]);
                                            ss[3+attrid1[l]] = new Integer(sw+Integer.parseInt(item4.rule)).toString();
                                            break;
                                        }
                                    }
                                }


                            }
                        }
                    }

                    ss[2] = s1;


                    int deojid=Insert(ss);
                    //插入到双向指针表
                    biPointerT.biPointerTable.add(new BiPointerTableItem(classid,tupleid,item.deputyid,deojid));



                }
            }
        }
        return tupleid;



    }

    private boolean Condition(String attrtype,Tuple tuple,int attrid,String value1){
        String value = value1.replace("\"","");
        switch (attrtype){
            case "int":
                int value_int = Integer.parseInt(value);
                if(Integer.parseInt((String)tuple.tuple[attrid])==value_int)
                    return true;
                break;
            case "char":
                String value_string = value;
                if(tuple.tuple[attrid].equals(value_string))
                    return true;
                break;

        }
        return false;
    }

    //DELETE FROM bb WHERE t4="5SS";
    //5,bb,t4,=,"5SS"
    private void Delete(String[] p) {
        String classname = p[1];
        String attrname = p[2];
        int classid = 0;
        int attrid=0;
        String attrtype=null;
        for (ClassTableItem item:classt.classTable) {
            if (item.classname.equals(classname) && item.attrname.equals(attrname)) {
                classid = item.classid;
                attrid = item.attrid;
                attrtype = item.attrtype;
                break;
            }
        }
        //寻找需要删除的
        OandB ob2 = new OandB();
        for (Iterator it1 = topt.objectTable.iterator(); it1.hasNext();){
            ObjectTableItem item = (ObjectTableItem)it1.next();
            if(item.classid == classid){
                Tuple tuple = GetTuple(item.blockid,item.offset);
                if(Condition(attrtype,tuple,attrid,p[4])){
                    //符合条件，即需要删除的元组
                    OandB ob =new OandB(DeletebyID(item.tupleid));
                    for(ObjectTableItem obj:ob.o){
                        ob2.o.add(obj);
                    }

                    for(BiPointerTableItem bip:ob.b){
                        ob2.b.add(bip);
                    }

                }
            }
        }
        for(ObjectTableItem obj:ob2.o){
            topt.objectTable.remove(obj);
        }
        for(BiPointerTableItem bip:ob2.b) {
            biPointerT.biPointerTable.remove(bip);
        }

    }

    private OandB DeletebyID(int id){

        List<ObjectTableItem> todelete1 = new ArrayList<>();
        List<BiPointerTableItem>todelete2 = new ArrayList<>();
        OandB ob = new OandB(todelete1,todelete2);
        for (Iterator it1 = topt.objectTable.iterator(); it1.hasNext();){
            ObjectTableItem item  = (ObjectTableItem)it1.next();
            if(item.tupleid == id){
                //需要删除的tuple


                //删除代理类的元组
                int deobid = 0;

                for(Iterator it = biPointerT.biPointerTable.iterator(); it.hasNext();){
                    BiPointerTableItem item1 =(BiPointerTableItem) it.next();
                    if(item.tupleid == item1.deputyobjectid){
                        //it.remove();
                        if(!todelete2.contains(item1))
                            todelete2.add(item1);
                    }
                    if(item.tupleid == item1.objectid){
                        deobid = item1.deputyobjectid;
                        OandB ob2=new OandB(DeletebyID(deobid));

                        for(ObjectTableItem obj:ob2.o){
                            if(!todelete1.contains(obj))
                                todelete1.add(obj);
                        }
                        for(BiPointerTableItem bip:ob2.b){
                            if(!todelete2.contains(bip))
                                todelete2.add(bip);
                        }

                        //biPointerT.biPointerTable.remove(item1);

                    }
                }

                ///////////////////////////////////////////////////
                //////////////////////新加代码部分///////////////////

                //由于class表中与此相关的已经清除掉了，所以不能通过class表找到classtype了
                //所以这里只能依靠判断双向指针表是否还作为其他的代理类判断了
                //注意此时双向指针表还没有清空，所以查找时，若发现作为被代理对象有两个item及以上，不能删除
                //计算待删除item作为代理对象 代理的对象数
                int obj_ori_num=0;

                for(Iterator it = biPointerT.biPointerTable.iterator(); it.hasNext();){
                    BiPointerTableItem bi_item =(BiPointerTableItem) it.next();
                    if(item.tupleid == bi_item.deputyobjectid){
                        obj_ori_num = obj_ori_num+1;
                    }
                }
                System.out.println("item.classid"+ item.classid);

                System.out.println("obj_ori_num"+ obj_ori_num);

                if(!(obj_ori_num>1)){
                    System.out.println("即将删除类id为"+item.classid+"tupleid为"+item.tupleid);
                    //如果不是 （此item所在的类的类型为'de_union'且其作为代理对象 代理的对象数>1（因为尚未删除原类中的那个对象））
                    //则删除此代理对象
                    DeleteTuple(item.blockid,item.offset);
                    if(!todelete1.contains(item)){
                        todelete1.add(item);
                    }
                }
                ///////////////////////////////////////////////////
                ///////////////////////////////////////////////////


            }
        }

        return ob;
    }

    public TupleList DirectSelect(String[] p){
        TupleList tpl = new TupleList();
        int attrnumber = Integer.parseInt(p[1]);
        String[] attrname = new String[attrnumber];
        int[] attrid = new int[attrnumber];
        String[] attrtype= new String[attrnumber];
        String classname = p[2+4*attrnumber];
        int classid = 0;
        for(int i = 0;i < attrnumber;i++){
            for (ClassTableItem item:classt.classTable) {
                if (item.classname.equals(classname) && item.attrname.equals(p[2+4*i])) {
                    classid = item.classid;
                    attrid[i] = item.attrid;
                    attrtype[i] = item.attrtype;
                    attrname[i] = p[5+4*i];
                    //重命名

                    break;
                }
            }
        }


        int sattrid = 0;
        String sattrtype = null;
        for (ClassTableItem item:classt.classTable) {
            if (item.classid == classid && item.attrname.equals(p[3+4*attrnumber])) {
                sattrid = item.attrid;
                sattrtype = item.attrtype;
                break;
            }
        }


        for(ObjectTableItem item : topt.objectTable){
            if(item.classid == classid){
                Tuple tuple = GetTuple(item.blockid,item.offset);
                if(Condition(sattrtype,tuple,sattrid,p[4*attrnumber+5])){
                    //Switch

                    for(int j = 0;j<attrnumber;j++){
                        if(Integer.parseInt(p[3+4*j])==1){
                            int value = Integer.parseInt(p[4+4*j]);
                            int orivalue = Integer.parseInt((String)tuple.tuple[attrid[j]]);
                            Object ob = value+orivalue;
                            tuple.tuple[attrid[j]] = ob;
                        }

                    }

                    tpl.addTuple(tuple);
                }
            }
        }
        for(int i =0;i<attrnumber;i++){
            attrid[i]=i;
        }
        PrintSelectResult(tpl,attrname,attrid,attrtype);
        return tpl;

    }
    //SELECT  b1+2 AS c1,b2 AS c2,b3 AS c3 FROM  bb WHERE t1="1";
    //6,3,b1,1,2,c1,b2,0,0,c2,b3,0,0,c3,bb,t1,=,"1"
    //0 1 2  3 4 5  6  7 8 9  10 111213 14 15 16 17
    public TupleList DirectSelectPublic(String[] p){
        TupleList tpl = new TupleList();
        int attrnumber = Integer.parseInt(p[1]);
        String[] attrname = new String[attrnumber];
        int[] attrid = new int[attrnumber];
        String[] attrtype= new String[attrnumber];
        String classname = p[2+4*attrnumber];
//        System.out.println(classname);

        int classid = 0;
        for(int i = 0;i < attrnumber;i++){
            for (ClassTableItem item:classt.classTable) {
//                System.out.println(p[2+4*i]);
//                System.out.println(item.attrname);
                if (item.classname.equals(classname) && item.attrname.equals(p[2+4*i])) {


                    classid = item.classid;
                    attrid[i] = item.attrid;
                    attrtype[i] = item.attrtype;
                    attrname[i] = p[5+4*i];
                    //重命名

                    break;
                }
            }
        }


        int sattrid = 0;
        String sattrtype = null;
        for (ClassTableItem item:classt.classTable) {
            if (item.classid == classid && item.attrname.equals(p[3+4*attrnumber])) {
                sattrid = item.attrid;
                sattrtype = item.attrtype;
                break;
            }
        }


        for(ObjectTableItem item : topt.objectTable){
//            System.out.println("进入objectTable");
//            System.out.println(item.classid);
//            System.out.println(classid);

            if(item.classid == classid){


                Tuple tuple = GetTuple(item.blockid,item.offset);
                if(Condition(sattrtype,tuple,sattrid,p[4*attrnumber+5])){
                    //Switch
                    System.out.println("ObjectTableItem满足项");
                    for(int j = 0;j<attrnumber;j++){
                        if(Integer.parseInt(p[3+4*j])==1){
                            int value = Integer.parseInt(p[4+4*j]);
                            int orivalue = Integer.parseInt((String)tuple.tuple[attrid[j]]);
                            Object ob = value+orivalue;
                            tuple.tuple[attrid[j]] = ob;
                        }

                    }

                    tpl.addTuple(tuple);
                }
            }
        }
        for(int i =0;i<attrnumber;i++){
            attrid[i]=i;
        }
        return tpl;

    }

    //SELECT popSinger -> singer.nation  FROM popSinger WHERE singerName = "JayZhou";
    //7,2,popSinger,singer,nation,popSinger,singerName,=,"JayZhou"
    //0 1 2         3      4      5         6          7  8
    private TupleList InDirectSelect(String[] p){
        TupleList tpl= new TupleList();
        String classname = p[3];
        String attrname = p[4];
        String crossname = p[2];
        String[] attrtype = new String[1];
        String[] con =new String[3];
        con[0] = p[6];
        con[1] = p[7];
        con[2] = p[8];

        int classid = 0;
        int crossid = 0;
        String crossattrtype = null;
        int crossattrid = 0;
        for(ClassTableItem item : classt.classTable){
            if(item.classname.equals(classname)){
                classid = item.classid;
                if(attrname.equals(item.attrname))
                    attrtype[0]=item.attrtype;
            }
            if(item.classname.equals(crossname)){
                crossid = item.classid;
                if(item.attrname.equals(con[0])) {
                    crossattrtype = item.attrtype;
                    crossattrid = item.attrid;
                }
            }
        }

        for(ObjectTableItem item1:topt.objectTable){
            if(item1.classid == crossid){
                Tuple tuple = GetTuple(item1.blockid,item1.offset);
                if(Condition(crossattrtype,tuple,crossattrid,con[2])){
                    for(BiPointerTableItem item3: biPointerT.biPointerTable){
                        if(item1.tupleid == item3.objectid&&item3.deputyid == classid){
                            for(ObjectTableItem item2: topt.objectTable){
                                if(item2.tupleid == item3.deputyobjectid){
                                    Tuple ituple = GetTuple(item2.blockid,item2.offset);
                                    tpl.addTuple(ituple);
                                }
                            }
                        }
                    }

                }
            }

        }
        String[] name = new String[1];
        name[0] = attrname;
        int[] id = new int[1];
        id[0] = 0;
        PrintSelectResult(tpl,name,id,attrtype);
        return tpl;




    }

    //UPDATE Song SET type = ‘jazz’WHERE songId = 100;
    //OPT_CREATE_UPDATE，Song，type，“jazz”，songId，=，100
    //0                  1     2      3        4      5  6
    private void Update(String[] p){
        String classname = p[1];
        String attrname = p[2];
        String cattrname = p[4];

        int classid = 0;
        int attrid = 0;
        String attrtype = null;
        int cattrid = 0;
        String cattrtype = null;
        for(ClassTableItem item :classt.classTable){
            if (item.classname.equals(classname)){
                classid = item.classid;
                break;
            }
        }
        for(ClassTableItem item1 :classt.classTable){
            if (item1.classid==classid&&item1.attrname.equals(attrname)){
                attrtype = item1.attrtype;
                attrid = item1.attrid;
            }
        }
        for(ClassTableItem item2 :classt.classTable){
            if (item2.classid==classid&&item2.attrname.equals(cattrname)){
                cattrtype = item2.attrtype;
                cattrid = item2.attrid;
            }
        }



        for(ObjectTableItem item3:topt.objectTable){
            if(item3.classid == classid){
                Tuple tuple = GetTuple(item3.blockid,item3.offset);
                if(Condition(cattrtype,tuple,cattrid,p[6])){
                    UpdatebyID(item3.tupleid,attrid,p[3].replace("\"",""));
                }
            }
        }


    }

//    private void UpdatebyID(int tupleid,int attrid,String value){
//        //待更新元组所在的类id
//        Tuple tuple_new = null;
//        //classid是源类id
//        int classid=0;
//        for(ObjectTableItem item: topt.objectTable){
//            if(item.tupleid ==tupleid){
//                classid = item.classid;
//                Tuple tuple = GetTuple(item.blockid,item.offset);
//                tuple.tuple[attrid] = value;
//                UpateTuple(tuple,item.blockid,item.offset);
//                Tuple tuple1 = GetTuple(item.blockid,item.offset);
//                UpateTuple(tuple1,item.blockid,item.offset);
//                tuple_new = GetTuple(item.blockid,item.offset);
//            }
//        }
//        assert tuple_new != null;
//
//        //根据attrid找回attrname
//        String attrname = null;
//        int attribute_num = 0;
//
//        for(ClassTableItem item2: classt.classTable){
//            if ((item2.attrid == attrid)&&(item2.classid==classid)){
//                attrname = item2.attrname;
//                attribute_num = item2.attrnum;
//                break;
//            }
//        }
//
//        String [] attrnames = new String[attribute_num];
//        int idx_now=0;
//        for(ClassTableItem item: classt.classTable){
//            if (item.classid==classid){
//                attrnames[idx_now]=item.attrname;
//                idx_now=idx_now+1;
//            }
//        }
//
//
//
//        //找到待更新元组所在的类id所对应的switch规则、所对应的代理类、之间的代理规则
//
//        //检查更新后的对象是否满足代理规则
//        boolean tuple_new_satisfied = Boolean.TRUE;
//
//        //代理规则是一个[,,]（举例：a=1）
//        String[] condition =new String[3];
//        //代理类id
//        int deputyid = 0;
//
//        //遍历deputy表，根据classid找到对应的项，并取出代理规则
//        for(DeputyTableItem deputy_item:deputyt.deputyTable){
//            if(deputy_item.originid == classid){
//                condition=deputy_item.deputyrule;
//                deputyid=deputy_item.deputyid;
//            }
//        }
//
//        int de_attribute_num = 0;
//        //根据代理类id遍历class表，找到代理类中的元组的属性个数
//        for(ClassTableItem item: classt.classTable){
//            if (item.classid==deputyid){
//                de_attribute_num = item.attrnum;
//                break;
//            }
//        }
//
//        //condition_attribute是代理规则的属性
//        String condition_attribute = condition[0];
//        //如果待更新属性==代理规则中的属性，才要考虑是否有变动
//        if (condition_attribute.equals(attrname)){
//            if(!(tuple_new.tuple[attrid]==condition[2])){
//                //只有这种情况：更新的属性是代理规则中的属性，且更新后的值不满足代理规则，才会为FALSE
//                //其他的情况一律默认为TRUE(比如说更新的属性根本不涉及代理规则中的属性，或者涉及了但是更新后依然满足)
//                tuple_new_satisfied=Boolean.FALSE;
//            }
//        }
//
//        System.out.println("tuple_new_satisfied"+tuple_new_satisfied+condition[2]);
//        /////////////////////////////////////////////////////////////////
//        /////////////////////////////////////////////////////////////////
//
//        //下面是这个元组原本是有代理对象的（也就是在双向指针表中有值），也就是说原本就是满足代理规则的
//        List<BiPointerTableItem> to_delete_bi_list = new ArrayList<>();
//        List<ObjectTableItem> to_delete_obj_list = new ArrayList<>();
//
//        for(BiPointerTableItem item1: biPointerT.biPointerTable) {
//            //双向指针表中找到此bi项
//            if (item1.objectid == tupleid) {
//                if(!(tuple_new_satisfied)){
//                    System.out.println("因为更新导致代理对象需要删除");
//                    //如果不满足了，直接删掉这个代理对象
//                    //包括：1、删除object表 2、删除双向指针表  3、删除该元组
//                    ObjectTableItem to_delete_object_item =null;
//                    for (ObjectTableItem obj_item : topt.objectTable) {
//                           if(obj_item.tupleid==item1.deputyobjectid){
//                                 to_delete_object_item = obj_item;
//                                 //找到了这个代理对象的object表项
//                           }
//                    }
//                    to_delete_obj_list.add(to_delete_object_item);
////                    topt.objectTable.remove(to_delete_object_item);
//                    BiPointerTableItem to_delete_bi_item =null;
//                    for (BiPointerTableItem bi_item : biPointerT.biPointerTable) {
//                        if(bi_item.deputyobjectid==item1.deputyobjectid){
//                            //找到了这个代理对象的biPointer表项
//                            to_delete_bi_item=bi_item;
//                        }
//                    }
//                    to_delete_bi_list.add(to_delete_bi_item);
////                    biPointerT.biPointerTable.remove(to_delete_bi_item);
//                    //删除该元组
//                    assert to_delete_object_item != null;
//                    DeleteTuple(to_delete_object_item.blockid,to_delete_object_item.offset);
//                }
//            }
//        }
//        for (BiPointerTableItem to_delete_bi : to_delete_bi_list) {
//            biPointerT.biPointerTable.remove(to_delete_bi);
//        }
//
//        /////////////////////////////////////////////////////////////////
//        /////////////////////////////////////////////////////////////////
//
//
//        for(BiPointerTableItem item1: biPointerT.biPointerTable) {
//            //双向指针表中找到此bi项
//            if (item1.objectid == tupleid) {
//
//                for(ClassTableItem item4:classt.classTable){
//
//                    //class表中找到此代理对象的所在的类的各个属性名
//                    if(item4.classid==item1.deputyid){
//                        //找到了代理这个tuple的属性名和属性id
//                        String dattrname = item4.attrname;
//                        int dattrid = item4.attrid;
//                        for (SwitchingTableItem item5 : switchingT.switchingTable) {
////                            System.out.println("因为更新1");
//
//                            //尝试能否找到switch规则，也就是看待更新属性和遍历的代理类的属性是否有switch关系，有的话才进行更新
//                            //并且找到switch规则，进行更新后，立马break出此switch循环
//
//                            String dswitchrule = null;
//                            String dvalue = null;
//                            System.out.println(attrname);
//                            System.out.println(dattrname);
//
//                            if (item5.attr.equals(attrname) && item5.deputy.equals(dattrname)) {
//                                //在switchingTable找到属性和代理属性
//                                dvalue = value;
//                                if (Integer.parseInt(item5.rule) != 0) {
//                                    dswitchrule = item5.rule;
//                                    dvalue = Integer.toString(Integer.parseInt(dvalue) + Integer.parseInt(dswitchrule));
//                                }
//                                UpdatebyID(item1.deputyobjectid, dattrid, dvalue);
//
//
//
//                                break;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        //如果原本这个对象无代理对象，也就是说在双向指针表中无内容
//        int bi_pointer_num=0;
//        for(BiPointerTableItem item1: biPointerT.biPointerTable) {
//            if (item1.objectid == tupleid) {
//                bi_pointer_num = bi_pointer_num+1;
//            }
//        }
//        ObjectTableItem to_add_obj_list = new ArrayList<>();
//
//        if (bi_pointer_num==0){
//            //则首先检查更新后的元组tuple1是否满足这里的代理规则
//            //如果满足，将在代理类下新建一个代理对象（注意属性值是switch后的），并在双向指针表中加入相关项
//            if(tuple_new_satisfied){
//                //新建一个代理对象，主要包括1、在object新建一个ObjectTableItem 2、在双向指针表新增一个BiPointerTableItem
//
//                Tuple d_tuple = new Tuple();
//
//                //ituple.tupleHeader是该代理对象元组的属性个数
//                d_tuple.tupleHeader = de_attribute_num;
//                //ituple的tuple为一个Object类型数组，其中存的是所有属性的具体值
//                d_tuple.tuple = new Object[de_attribute_num];
//
//                //现根据源类中的每一个属性名，在switch表中找到代理类的属性名，并根据需要选择是否switch
//                int de_idx_now=0;
//                for (int i = 0; i < attribute_num; i++) {
//                    for (SwitchingTableItem item:switchingT.switchingTable) {
//                        if(item.attr.equals(attrnames[i])){
//                            if(Integer.parseInt(item.rule)!=0){
//                                String dswitchrule = item.rule;
//                                Object ob = Integer.parseInt((String)tuple_new.tuple[i]) + Integer.parseInt(dswitchrule);
//                                d_tuple.tuple[de_idx_now] = ob;
//
//                            }
//                            else {
//                                d_tuple.tuple[de_idx_now]=tuple_new.tuple[i];
//                            }
//                            de_idx_now=de_idx_now+1;
//                        }
//                    }
//
//                }
//                //把元组最大id+1，表示要新增一个对象了，这个对象属于代理类
//                topt.maxTupleId++;
//                int tupid = topt.maxTupleId;
//
//                int [] aa = InsertTuple(d_tuple);
//
//                //1、在object新建一个ObjectTableItem
//                //代理类id、代理对象id（属于这个代理类）、块id、块内偏移
//
//                topt.objectTable.add(new ObjectTableItem(deputyid,tupid,aa[0],aa[1]));
//
//                //2、在双向指针表新增一个BiPointerTableItem
//                //源类id、待更新的tupleid、代理类id、刚才新加的对象的tupleid（这个对象是属于代理类的）
//                biPointerT.biPointerTable.add(new BiPointerTableItem(classid,tupleid,deputyid,tupid));
//                /////////////////////////////////////////////////////////////////////////////
//                /////////////////////////////////////////////////////////////////////////////
//
//            }
//
//        }
//
//
//
//
//
//
//    }

    private void UpdatebyID(int tupleid,int attrid,String value){
        for(ObjectTableItem item: topt.objectTable){
            if(item.tupleid ==tupleid){
                Tuple tuple = GetTuple(item.blockid,item.offset);
                tuple.tuple[attrid] = value;
                UpateTuple(tuple,item.blockid,item.offset);
                Tuple tuple1 = GetTuple(item.blockid,item.offset);
                UpateTuple(tuple1,item.blockid,item.offset);
            }
        }

        String attrname = null;
        for(ClassTableItem item2: classt.classTable){
            if (item2.attrid == attrid){
                attrname = item2.attrname;
                break;
            }
        }
        for(BiPointerTableItem item1: biPointerT.biPointerTable) {
            if (item1.objectid == tupleid) {


                for(ClassTableItem item4:classt.classTable){
                    if(item4.classid==item1.deputyid){
                        String dattrname = item4.attrname;
                        int dattrid = item4.attrid;
                        for (SwitchingTableItem item5 : switchingT.switchingTable) {
                            String dswitchrule = null;
                            String dvalue = null;
                            if (item5.attr.equals(attrname) && item5.deputy.equals(dattrname)) {
                                dvalue = value;
                                if (Integer.parseInt(item5.rule) != 0) {
                                    dswitchrule = item5.rule;
                                    dvalue = Integer.toString(Integer.parseInt(dvalue) + Integer.parseInt(dswitchrule));
                                }
                                UpdatebyID(item1.deputyobjectid, dattrid, dvalue);
                                break;
                            }
                        }
                    }
                }
            }
        }

    }

    private class OandB{
        public List<ObjectTableItem> o= new ArrayList<>();
        public List<BiPointerTableItem> b= new ArrayList<>();
        public OandB(){}
        public OandB(OandB oandB){
            this.o = oandB.o;
            this.b = oandB.b;
        }

        public OandB(List<ObjectTableItem> o, List<BiPointerTableItem> b) {
            this.o = o;
            this.b = b;
        }
    }

    private Tuple GetTuple(int id, int offset) {

        return mem.readTuple(id,offset);
    }

    private int[] InsertTuple(Tuple tuple){
        return mem.writeTuple(tuple);
    }

    private void DeleteTuple(int id, int offset){
        mem.deleteTuple();
        return;
    }

    private void UpateTuple(Tuple tuple,int blockid,int offset){
        mem.UpateTuple(tuple,blockid,offset);
    }

    private void PrintTab(ObjectTable topt,SwitchingTable switchingT,DeputyTable deputyt,BiPointerTable biPointerT,ClassTable classTable) {
        Intent intent = new Intent(context, ShowTable.class);

        Bundle bundle0 = new Bundle();
        bundle0.putSerializable("ObjectTable",topt);
        bundle0.putSerializable("SwitchingTable",switchingT);
        bundle0.putSerializable("DeputyTable",deputyt);
        bundle0.putSerializable("BiPointerTable",biPointerT);
        bundle0.putSerializable("ClassTable",classTable);
        intent.putExtras(bundle0);
        context.startActivity(intent);

    }

    private void PrintSelectResult(TupleList tpl, String[] attrname, int[] attrid, String[] type) {
        Intent intent = new Intent(context, PrintResult.class);


        Bundle bundle = new Bundle();
        bundle.putSerializable("tupleList", tpl);
        bundle.putStringArray("attrname", attrname);
        bundle.putIntArray("attrid", attrid);
        bundle.putStringArray("type", type);
        intent.putExtras(bundle);
        context.startActivity(intent);


    }
    public void PrintTab(){
        PrintTab(topt,switchingT,deputyt,biPointerT,classt);
    }


    public String[] appTableCreate(String app_name) {
        String[] p = new String[9];
        String[] attr_name_list = new String[3];
        p[0] = parse.OPT_CREATE_ORIGINCLASS+"";
        p[1] = attr_name_list.length+"";
        p[2] = app_name;

        p[3] = app_name+"_longitude";
        attr_name_list[0] = p[3];
        p[4] = "int";

        p[5] = app_name+"_latitude";
        attr_name_list[1] = p[5];
        p[6] = "int";

        p[7] = app_name+"_flag";
        attr_name_list[2] = p[7];
        p[8] = "int";

        CreateOriginClass(p);

        return attr_name_list;
    }

    public void appTupleCreate(String class_name, String longitude, String latitude) {
        String[] p = new String[6];
        p[0] = parse.OPT_INSERT+"";
        p[1] = p.length-3+"";
        p[2] = class_name;
        p[3] = longitude;
        p[4] = latitude;
        p[5] = "1";

        Insert(p);
        return;
    }

    public void appUnionCreate(String[] app_names, ArrayList<String[]> attr_name_lists) {
        int select_cnt = app_names.length;
        int attr_cnt = 3;
        int attr_block_size = 4;
        int select_block_size = attr_cnt*attr_block_size+4;

        String[] p = new String[4+select_cnt*select_block_size];
        String[] bdattr_name_list = new String[] {"lon", "lat", "flag"};

//		System.out.println(app_names.get(0));
        p[0] = parse.OPT_CREATE_UNIONDEPUTY+"";
        p[1] = select_cnt+"";
        p[2] = attr_cnt+"";
        p[3] = "utrack";
        for(int i=0;i<select_cnt;i++) {
            for(int j=0;j<attr_cnt;j++) {
                int attr_offset = i*select_block_size+j*attr_block_size;
                p[4+attr_offset] = attr_name_lists.get(i)[j];
                p[5+attr_offset] = "0";
                p[6+attr_offset] = "0";
                p[7+attr_offset] = bdattr_name_list[j];
            }
            int select_offset = i*select_block_size+attr_cnt*attr_block_size;
            p[4+select_offset] = app_names[i];
            p[5+select_offset] = app_names[i]+"_flag";
            p[6+select_offset] = "=";
            p[7+select_offset] = "1";
        }
        CreateUnionDeputy(p);
    }
}

