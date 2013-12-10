/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mcongrove.glass.chucknorris;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.TimelineManager;

/**
 * Service owning the LiveCard living in the timeline.
 */
public class JokeService extends Service {

    private static final String LIVE_CARD_ID = "ChuckNorris";

    /**
     * Binder giving access to the underlying {@code Timer}.
     */
    public class JokeBinder extends Binder {
        public void getNewJoke() {
        	getJoke();
        }
	}
	
	private final JokeBinder mBinder = new JokeBinder();

    private JokeDrawer mJokeDrawer;
    private TimelineManager mTimelineManager;
    private LiveCard mLiveCard;
    private TextToSpeech mSpeech;

    @Override
    public void onCreate() {
    	Log.d(LIVE_CARD_ID, "onCreate");
    	
        super.onCreate();
        
        mTimelineManager = TimelineManager.from(this);
        
        mJokeDrawer = new JokeDrawer(this);
        
        mSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                Log.d(LIVE_CARD_ID, "TextToSpeech:onInit");
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    private class RetrieveJoke extends AsyncTask < Void, Void, String > {

    	private static final String TAG = "RetrieveJoke";

    	@Override
    	protected String doInBackground(Void...params) {
    		StringBuilder builder = new StringBuilder();
    		HttpClient client = new DefaultHttpClient();
    		HttpGet httpGet = new HttpGet("http://api.icndb.com/jokes/random?exclude=[nerdy,explicit]&escape=javascript");

    		try {
    			HttpResponse response = client.execute(httpGet);
    			StatusLine statusLine = response.getStatusLine();
    			
    			int statusCode = statusLine.getStatusCode();
    			
    			if (statusCode == 200) {
    				HttpEntity entity = response.getEntity();
    				InputStream content = entity.getContent();
    				
    				BufferedReader reader = new BufferedReader(
    						new InputStreamReader(content)
    				);
    				
    				String line;
    				
    				while ((line = reader.readLine()) != null) {
    					builder.append(line);
    				}
    				
    				Log.v(TAG, "Your data: " + builder.toString());
    				
    				JSONObject json = new JSONObject(new JSONTokener(builder.toString()));
    				JSONObject value = json.getJSONObject("value");
    				String joke = value.getString("joke");
    				
    				return joke;
    			} else {
    				Log.e(TAG, "Failed to download file");
    			}
    		} catch (ClientProtocolException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		} catch (JSONException e) {
    			e.printStackTrace();
    		}

    		return null;
    	}

    	@Override
    	protected void onPostExecute(String result) {
    		setJoke(result);
    	}

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	Log.d(LIVE_CARD_ID, "onStartCommand");
    	
        if (mLiveCard == null) {
            mLiveCard = mTimelineManager.getLiveCard(LIVE_CARD_ID);

            mLiveCard.enableDirectRendering(true).getSurfaceHolder().addCallback(mJokeDrawer);
            mLiveCard.setNonSilent(true);

            Intent menuIntent = new Intent(this, MenuActivity.class);
            mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));

            mLiveCard.publish();
            
            getJoke();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mLiveCard != null && mLiveCard.isPublished()) {
            mLiveCard.getSurfaceHolder().removeCallback(mJokeDrawer);
            mLiveCard.unpublish();
            mLiveCard = null;
        }
        
        mSpeech.shutdown();
        mSpeech = null;
        
        super.onDestroy();
    }
    
    public void getJoke() {
    	RetrieveJoke get = new RetrieveJoke();
    	
        get.execute();
    }
    
    public void setJoke(String joke) {
    	//String joke = "According to the Bible, God created the universe in six days. Before that, Chuck Norris created God by snapping his fingers.";
    	
    	mJokeDrawer.setJoke(joke);
    	
    	tts(joke);
    }
    
    public void tts(String words) {
    	mSpeech.speak(words, TextToSpeech.QUEUE_FLUSH, null);
	}
}
