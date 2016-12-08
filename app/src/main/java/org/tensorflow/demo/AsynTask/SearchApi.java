package org.tensorflow.demo.AsynTask;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.demo.Interface.AsyncResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;

/**
 * Created by amrit on 11/28/2016.
 */

public class SearchApi extends AsyncTask<String,Void, String[]> {
    private static final String LOG_TAG = SearchApi. class.getSimpleName();
    public AsyncResponse mresponse ;

    @Override
    protected String[] doInBackground(String... params) {
        //Do your network operation here

        BufferedReader reader = null;
        HttpURLConnection conn = null;

        String searchapi = "http://www.gofindapi.com:3000/searchapi";

        String CONTENTTYPE_PARAM = "Content-Type";
        String contentType = "application/x-www-form-urlencoded";

        String searchJsonStr = null;
        String img_url = params[0];

        URL url = null;
        try {
            url = new URL(searchapi);
            conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty(CONTENTTYPE_PARAM, contentType);

            StringBuilder sb = new StringBuilder();
            sb.append("img64").append("=").append(URLEncoder.encode(img_url, "utf-8"));

            byte[] entity = sb.toString().getBytes();
            OutputStream os = conn.getOutputStream();
            os.write(entity);
            os.flush();


            InputStream inputStream = conn.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                return null;
            }
            searchJsonStr = buffer.toString();
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        } catch (ProtocolException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        } finally {
            conn.disconnect();
        }

        JSONObject obj = null;
        try {
            obj = new JSONObject(searchJsonStr);
            JSONArray jsondata = obj.getJSONArray("data");
            String[] arr = new String[jsondata.length()];

            for (int i = 0; i < jsondata.length(); i++) {
                JSONObject obj_each = jsondata.optJSONObject(i);
                JSONArray image_list = obj_each.getJSONArray("reference_image_links");
                arr[i] = image_list.getString(0);
                Log.d("My App", arr[i]);
                return arr;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("My App", "Could not parse malformed JSON: \"" + searchJsonStr + "\"");
        }
        return null;
    }

    @Override
    protected void onPostExecute(String[] result) {
        Log.e(LOG_TAG, "Result : "+ Arrays.deepToString(result));
        if(result!=null && result.length>0 && mresponse!=null)
            mresponse.processFinish(result);
    }

}
