package io.github.hiro.lime.hooks;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;


public class ReadChecker implements IHook {
    private SQLiteDatabase limeDatabase;
    private SQLiteDatabase db3 = null; // クラスフィールドとして宣言
    private SQLiteDatabase db4 = null; // クラスフィールドとして宣言
    private boolean shouldHookOnCreate = false;
    private String currentGroupId = null;


    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Application appContext = (Application) param.thisObject;


                if (appContext == null) {
                    return;
                }


                File dbFile3 = appContext.getDatabasePath("naver_line");
                File dbFile4 = appContext.getDatabasePath("contact");


                if (dbFile3.exists() && dbFile4.exists()) {
                    SQLiteDatabase.OpenParams.Builder builder1 = new SQLiteDatabase.OpenParams.Builder();
                    builder1.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                    SQLiteDatabase.OpenParams dbParams1 = builder1.build();


                    SQLiteDatabase.OpenParams.Builder builder2 = new SQLiteDatabase.OpenParams.Builder();
                    builder2.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                    SQLiteDatabase.OpenParams dbParams2 = builder2.build();


                    db3 = SQLiteDatabase.openDatabase(dbFile3, dbParams1); // フィールドに代入
                    db4 = SQLiteDatabase.openDatabase(dbFile4, dbParams2); // フィールドに代入


                    // データベースの初期化
                    initializeLimeDatabase(appContext);


                    // データの取得
                    Catcha(loadPackageParam, db3, db4); // ここでフィールドを使って呼び出す
                }
            }
        });


        Class<?> chatHistoryRequestClass = XposedHelpers.findClass("com.linecorp.line.chat.request.ChatHistoryRequest", loadPackageParam.classLoader);
        XposedHelpers.findAndHookMethod(chatHistoryRequestClass, "getChatId", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                String chatId = (String) param.getResult();
                //XposedBridge.log(chatId);
                if (isGroupExists(chatId)) {
                    shouldHookOnCreate = true;
                    currentGroupId = chatId; // groupIdを保存
                } else {
                    shouldHookOnCreate = false;
                    currentGroupId = null;
                }
            }
        });


        Class<?> chatHistoryActivityClass = XposedHelpers.findClass("jp.naver.line.android.activity.chathistory.ChatHistoryActivity", loadPackageParam.classLoader);
        XposedHelpers.findAndHookMethod(chatHistoryActivityClass, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (shouldHookOnCreate && currentGroupId != null) {
                    // isNoGroupメソッドを使用して、グループが存在しない場合はボタンを追加しない
                    if (!isNoGroup(currentGroupId)) {
                        Activity activity = (Activity) param.thisObject;
                        addButton(activity);
                    }
                }
            }
        });


    }

    private boolean isGroupExists(String groupId) {
        if (limeDatabase == null) {
            XposedBridge.log("Database is not initialized.");
            return false;
        }


        String query = "SELECT 1 FROM group_messages WHERE group_id = ?";
        Cursor cursor = limeDatabase.rawQuery(query, new String[]{groupId});
        boolean exists = cursor.moveToFirst();
        cursor.close();


        return exists;
    }

    private boolean isNoGroup(String groupId) {
        if (limeDatabase == null) {
            XposedBridge.log("Database is not initialized.");
            return true; // データベースが初期化されていない場合はグループがないと見なす
        }

        // グループ名を取得するためのクエリ
        String query = "SELECT group_name FROM group_messages WHERE group_id = ?";
        Cursor cursor = limeDatabase.rawQuery(query, new String[]{groupId});

        // グループ名が存在するかどうかのチェック
        boolean noGroup = true; // 初期値としてグループがないと見なす

        if (cursor.moveToFirst()) {
            String groupName = cursor.getString(cursor.getColumnIndex("group_name"));
            // グループ名が存在する場合はnoGroupをfalseに設定
            noGroup = groupName == null || groupName.isEmpty();
        }

        cursor.close();
        return noGroup;
    }

    // ボタンを追加するメソッド
    private void addButton(Activity activity) {
        Button button = new Button(activity);
        button.setText("既読データ表示");


        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        frameParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        frameParams.topMargin = 150;
        button.setLayoutParams(frameParams);


        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentGroupId != null) {
                    showDataForGroupId(activity, currentGroupId); // ボタンクリック時にデータを表示
                }
            }
        });


        ViewGroup layout = activity.findViewById(android.R.id.content);
        layout.addView(button);
    }

    private void showDataForGroupId(Activity activity, String groupId) {
        if (limeDatabase == null) {
            return;
        }

        // group_idに対応する全てのserver_id, content, created_timeを取得
        String query = "SELECT server_id, content, created_time FROM group_messages WHERE group_id=? ORDER BY created_time ASC";
        Cursor cursor = limeDatabase.rawQuery(query, new String[]{groupId});

        // server_idごとのデータを保持するマップ
        Map<String, DataItem> dataItemMap = new HashMap<>();

        // データをマップに格納
        while (cursor.moveToNext()) {
            String serverId = cursor.getString(0);
            String content = cursor.getString(1);
            String createdTime = cursor.getString(2);

            // 既読者リストを取得
            List<String> talkNameList = getTalkNamesForServerId(serverId);

            // 既存のデータがある場合は、既読者を追加しないように重複を排除
            if (dataItemMap.containsKey(serverId)) {
                DataItem existingItem = dataItemMap.get(serverId);
                existingItem.talkNames.addAll(talkNameList);
            } else {
                // 新しいDataItemを作成
                DataItem dataItem = new DataItem(serverId, content, createdTime);
                dataItem.talkNames.addAll(talkNameList);
                dataItemMap.put(serverId, dataItem);
            }
        }
        cursor.close();

        // 結果を表示
        StringBuilder resultBuilder = new StringBuilder();
        for (DataItem item : dataItemMap.values()) {
            resultBuilder.append("Content: ").append(item.content != null ? item.content : "Media").append("\n");
            resultBuilder.append("Created Time: ").append(item.createdTime).append("\n");

            if (!item.talkNames.isEmpty()) {
                resultBuilder.append("既読者 (").append(item.talkNames.size()).append("):\n");
                for (String talkName : item.talkNames) {
                    resultBuilder.append("- ").append(talkName).append("\n");
                }
            } else {
                resultBuilder.append("No talk names found.\n");
            }
            resultBuilder.append("\n");
        }

        // ScrollViewを使って結果を表示
        TextView textView = new TextView(activity);
        textView.setText(resultBuilder.toString());
        textView.setPadding(20, 20, 20, 20);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(textView);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Group Data");
        builder.setView(scrollView);
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private static class DataItem {
        String serverId;
        String content;
        String createdTime;
        Set<String> talkNames;

        DataItem(String serverId, String content, String createdTime) {
            this.serverId = serverId;
            this.content = content;
            this.createdTime = createdTime;
            this.talkNames = new HashSet<>();
        }
    }

    private List<String> getTalkNamesForServerId(String serverId) {
        List<String> talkNames = new ArrayList<>();
        if (limeDatabase == null) {
            return talkNames;
        }
        String query = "SELECT DISTINCT talk_name FROM group_messages WHERE server_id=?";
        Cursor cursor = limeDatabase.rawQuery(query, new String[]{serverId});

        while (cursor.moveToNext()) {
            String talkName = cursor.getString(0);
            if (talkName != null) {
                talkNames.add(talkName);
            }
        }
        cursor.close();
        return talkNames;
    }


    private void Catcha(XC_LoadPackage.LoadPackageParam loadPackageParam, SQLiteDatabase db3, SQLiteDatabase db4) {
        try {
            XposedBridge.hookAllMethods(
                    loadPackageParam.classLoader.loadClass(Constants.RESPONSE_HOOK.className),
                    Constants.RESPONSE_HOOK.methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String paramValue = param.args[1].toString();
                           // XposedBridge.log(paramValue);


                            if (paramValue.contains("type:NOTIFIED_READ_MESSAGE")) {
                              //  XposedBridge.log(paramValue);
                                // Fetch data and save it to the database
                                fetchDataAndSave(db3, db4, paramValue); // db3とdb4を渡す
                            }
                        }
                    }
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


    private void fetchDataAndSave(SQLiteDatabase db3, SQLiteDatabase db4, String paramValue) {
        // param1, param2, param3をそれぞれ抽出


        String serverId = extractServerId(paramValue);
        String checkedUser = extractCheckedUser(paramValue);


        if (serverId == null ||  checkedUser == null) {
            // XposedBridge.log("Missing parameters: serverId=" + serverId + ", groupId=" + groupId + ", checkedUser=" + checkedUser);
            return;
        }
        String groupId = queryDatabase(db3, "SELECT chat_id FROM chat_history WHERE server_id=?", serverId);
        String groupName = queryDatabase(db3, "SELECT name FROM groups WHERE id=?", groupId);
        if (groupName == null) {
            return;
        }

        String content = queryDatabase(db3, "SELECT content FROM chat_history WHERE server_id=?", serverId);

        String talkName = queryDatabase(db4, "SELECT profile_name FROM contacts WHERE mid=?", checkedUser);
        String timeEpochStr = queryDatabase(db3, "SELECT created_time FROM chat_history WHERE server_id=?", serverId);
        String timeFormatted = formatMessageTime(timeEpochStr);


        saveData(groupId, serverId, checkedUser, groupName, content, talkName, timeFormatted);
    }


    private String formatMessageTime(String timeEpochStr) {
        if (timeEpochStr == null) return null;
        long timeEpoch = Long.parseLong(timeEpochStr);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timeEpoch));
    }


    private String extractGroupId(String paramValue) {
        Pattern pattern = Pattern.compile("param1:([a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(paramValue);
        return matcher.find() ? matcher.group(1) : null;
    }


    private String extractServerId(String paramValue) {
        Pattern pattern = Pattern.compile("param3:([0-9]+)");
        Matcher matcher = pattern.matcher(paramValue);
        return matcher.find() ? matcher.group(1) : null;
    }


    private String extractCheckedUser(String paramValue) {
        Pattern pattern = Pattern.compile("param2:([a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(paramValue);
        return matcher.find() ? matcher.group(1) : null;
    }




    private String queryDatabase(SQLiteDatabase db, String query, String... selectionArgs) {
        if (db == null) {
            XposedBridge.log("Database is not initialized.");
            return null;
        }
        Cursor cursor = db.rawQuery(query, selectionArgs);
        String result = null;
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        }
        cursor.close();
        return result;
    }


    private void initializeLimeDatabase(Context context) {
        File dbFile = new File(context.getFilesDir(), "lime_data.db");
        limeDatabase = SQLiteDatabase.openOrCreateDatabase(dbFile, null);


        // 既存のテーブルがあれば削除する（デモ用に行いますが、実際のアプリでは注意が必要です）
        String dropTableIfExists = "DROP TABLE IF EXISTS group_messages;";
        limeDatabase.execSQL(dropTableIfExists);


        // 新しいテーブルを作成
        String createGroupTable = "CREATE TABLE IF NOT EXISTS group_messages (" +
                "group_id TEXT NOT NULL," +
                "server_id TEXT NOT NULL," +
                "checked_user TEXT," +
                "group_name TEXT," +
                "content TEXT," +
                "talk_name TEXT," +
                "created_time TEXT," + // ここで created_time カラムを追加
                "PRIMARY KEY (group_id, server_id, checked_user)" +
                ");";


        limeDatabase.execSQL(createGroupTable);
        XposedBridge.log("Database initialized and group_messages table created.");
    }




    private void saveData(String groupId, String serverId, String checkedUser, String groupName, String content, String talkName, String createdTime) {
        if (limeDatabase == null) {


            return;
        }




        String checkQuery = "SELECT COUNT(*) FROM group_messages WHERE server_id=? AND checked_user=?";
        Cursor cursor = limeDatabase.rawQuery(checkQuery, new String[]{serverId, checkedUser});
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();


        // 既存のデータがない場合のみ書き込む
        if (count > 0) {
            //XposedBridge.log("Data already exists for Server_Id: " + serverId + ", Checked_user: " + checkedUser + ". Skipping save.");
            return;
        }


        String insertOrUpdateQuery = "INSERT INTO group_messages (group_id, server_id, checked_user, group_name, content, talk_name, created_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?);"; // created_timeを追加


        limeDatabase.execSQL(insertOrUpdateQuery, new Object[]{groupId, serverId, checkedUser, groupName, content, talkName, createdTime}); // created_timeも追加


      //  XposedBridge.log("Saved to DB: Group_Id: " + groupId + ", Server_id: " + serverId + ", Checked_user: " + checkedUser +
        //        ", Group_Name: " + groupName + ", Content: " + content + ", Talk_Name: " + talkName + ", Created_Time: " + createdTime);
    }


}
