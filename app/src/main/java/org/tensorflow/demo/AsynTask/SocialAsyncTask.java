package org.tensorflow.demo.AsynTask;

import android.net.Uri;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;

public class SocialAsyncTask extends AsyncTask<String,Void,String[]> {

    private static final String LOG_TAG = SocialAsyncTask.class.getSimpleName() ;
    public AsyncResponse response ;
    @Override
    protected String[] doInBackground(String... params) {

        if(params.length==0)
            return null;

        String social_base_url = "http://50.23.125.197:3000/api";
        String FEED_PARAM = "feed";
        String LENGTH_PARAM = "length";
        String SKIP_PARAM = "skip";
        String REQUESTTYPE_PARAM = "Content-Type";

        int length=20;
        int skip=10;
        String requestType = "application/x-www-form-urlencoded";

        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        String socialJsonStr = null;

        try {
            Uri uri = Uri.parse(social_base_url);

            URL url =  new URL(uri.toString());

            Log.e(LOG_TAG,"URL= " + url);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty(REQUESTTYPE_PARAM, requestType );
            urlConnection.connect();

            StringBuilder sb = new StringBuilder();
            sb.append(FEED_PARAM).append("=").append(URLEncoder.encode(params[0], "utf-8"));
            sb.append("&");
            sb.append(LENGTH_PARAM).append("=").append(URLEncoder.encode(Integer.toString(length), "utf-8"));
            sb.append("&");
            sb.append(SKIP_PARAM).append("=").append(URLEncoder.encode(Integer.toString(skip), "utf-8"));

            byte[] entity = sb.toString().getBytes();

            OutputStream os = urlConnection.getOutputStream();
            os.write(entity);
            os.flush();

            InputStream inputStream = urlConnection.getInputStream();
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
            socialJsonStr = buffer.toString();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            return null;
        } finally{
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }

        Log.e(LOG_TAG, "JSONResponse = "+socialJsonStr);

        JSONObject result = null, look = null;
        JSONArray data = null;
        String image = null;
        int dataLength=0;
        try {
            result = new JSONObject(socialJsonStr);
            data = result.getJSONArray("data");
            dataLength = data.length();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(dataLength==0)
            return null;
        String[] images_url = new String[data.length()];
        try {
            for(int i=0;i<data.length();i++) {
                look = data.getJSONObject(i).getJSONObject("look");
                image = look.getString("image");
                images_url[i] = image;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(images_url!=null) {
            return images_url;
        }
        return null;
    }

    @Override
    protected void onPostExecute(String[] result) {
        Log.e(LOG_TAG, "Result : "+ Arrays.deepToString(result));
        if(result!=null && result.length>0)
            response.processFinish(result);
    }
}
