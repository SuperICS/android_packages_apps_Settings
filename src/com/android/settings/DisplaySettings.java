/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.ActivityManagerNative;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.IWindowManager;
import android.view.Surface;

import com.android.settings.cyanogenmod.DisplayRotation;
import com.android.settings.R;

import java.util.ArrayList;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_AUTOMATIC_BACKLIGHT = "backlight_widget";
    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_BATTERY_PULSE = "battery_pulse";
    private static final String KEY_DISPLAY_ROTATION = "display_rotation";
    private static final String KEY_VOLUME_WAKE = "pref_volume_wake";
    private static final String KEY_TRACKBALL_WAKE = "pref_trackball_wake";
    private static final String KEY_HDMI_RESOLUTION = "hdmi_resolution";
    private static final String KEY_HDMI_IGNORE_GSENSOR = "hdmi_ignore_gsensor";
    private static final String KEY_ACCELEROMETER_COORDINATE = "accelerometer_coordinate";
    

    private static final String ROTATION_ANGLE_0 = "0";
    private static final String ROTATION_ANGLE_90 = "90";
    private static final String ROTATION_ANGLE_180 = "180";
    private static final String ROTATION_ANGLE_270 = "270";
    private static final String ROTATION_ANGLE_DELIM = ", ";
    private static final String ROTATION_ANGLE_DELIM_FINAL = " & ";

    private CheckBoxPreference mVolumeWake;
    private CheckBoxPreference mTrackballWake;
    private CheckBoxPreference mAccelerometer;
    private ListPreference mFontSizePref;
    private CheckBoxPreference mNotificationPulse;
    private CheckBoxPreference mBatteryPulse;
    //private PreferenceScreen mNotificationPulse;

    private final Configuration mCurConfig = new Configuration();

    private ListPreference mScreenTimeoutPreference;
    private PreferenceScreen mDisplayRotationPreference;
    private PreferenceScreen mAutomaticBacklightPreference;

    private ListPreference mHdmiResolution;
    private CheckBoxPreference mHdmiIgnoreGsensor;
    private ListPreference mAccelerometerCoordinate;

    private ContentObserver mAccelerometerRotationObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            //updateDisplayRotationPreferenceDescription();
            updateAccelerometerRotationCheckbox();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);

        //mDisplayRotationPreference = (PreferenceScreen) findPreference(KEY_DISPLAY_ROTATION);
        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        final long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);
        updateDisplayRotationPreferenceDescription();

        mAutomaticBacklightPreference = (PreferenceScreen) findPreference(KEY_AUTOMATIC_BACKLIGHT);
        if (mAutomaticBacklightPreference != null
                && !getResources().getBoolean(
                        com.android.internal.R.bool.config_automatic_brightness_available)) {
            getPreferenceScreen().removePreference(mAutomaticBacklightPreference);
        }

        mFontSizePref = (ListPreference) findPreference(KEY_FONT_SIZE);
        mFontSizePref.setOnPreferenceChangeListener(this);
        /*mNotificationPulse = (PreferenceScreen) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse != null) {
            if (!getResources().getBoolean(com.android.internal.R.bool.config_intrusiveNotificationLed)) {
                getPreferenceScreen().removePreference(mNotificationPulse);
            } else {
                updateLightPulseDescription();*/
        mNotificationPulse = (CheckBoxPreference) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveNotificationLed) == false) {
            getPreferenceScreen().removePreference(mNotificationPulse);
        } else {
            try {
                mNotificationPulse.setChecked(Settings.System.getInt(resolver,
                        Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
                mNotificationPulse.setOnPreferenceChangeListener(this);
            } catch (SettingNotFoundException snfe) {
                Log.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
            }
        }

        mBatteryPulse = (CheckBoxPreference) findPreference(KEY_BATTERY_PULSE);
        if (mBatteryPulse != null) {
            if (getResources().getBoolean(
                    com.android.internal.R.bool.config_intrusiveBatteryLed) == false) {
                getPreferenceScreen().removePreference(mBatteryPulse);
            } else {
                mBatteryPulse.setChecked(Settings.System.getInt(resolver,
                        Settings.System.BATTERY_LIGHT_PULSE, 1) == 1);
                mBatteryPulse.setOnPreferenceChangeListener(this);
            }
        }
		mHdmiResolution = (ListPreference) findPreference(KEY_HDMI_RESOLUTION);
        if(mHdmiResolution != null){
            mHdmiResolution.setOnPreferenceChangeListener(this);
            String value = Settings.System.getString(getContentResolver(),
                    Settings.System.HDMI_RESOLUTION);
            mHdmiResolution.setValue(value);
            updateHdmiResolutionSummary(value);
        }
        mHdmiIgnoreGsensor = (CheckBoxPreference) findPreference(KEY_HDMI_IGNORE_GSENSOR);
        if (mHdmiIgnoreGsensor != null) {
        	mHdmiIgnoreGsensor.setChecked(Settings.System.getInt(resolver,
        		Settings.System.HDMI_IGNORE_GSENSOR, 1) == 1);
        }
        mAccelerometerCoordinate = (ListPreference) findPreference(KEY_ACCELEROMETER_COORDINATE);
        if(mAccelerometerCoordinate != null){
        	mAccelerometerCoordinate.setOnPreferenceChangeListener(this);
        	String value = Settings.System.getString(getContentResolver(),
        		Settings.System.ACCELEROMETER_COORDINATE);
        	mAccelerometerCoordinate.setValue(value);
        	updateAccelerometerCoordinateSummary(value);
        }
        

        mVolumeWake = (CheckBoxPreference) findPreference(KEY_VOLUME_WAKE);
        if (mVolumeWake != null) {
            mVolumeWake.setChecked(Settings.System.getInt(resolver,
                    Settings.System.VOLUME_WAKE_SCREEN, 0) == 1);

        }
        
        mTrackballWake = (CheckBoxPreference) findPreference(KEY_TRACKBALL_WAKE);
        if (mTrackballWake != null) {
            mTrackballWake.setChecked(Settings.System.getInt(resolver,
                    Settings.System.LOCKSCREEN_ENABLE_TRACKBALL_KEY, 0) == 1);

        }
    }

    private void updateDisplayRotationPreferenceDescription() {
        PreferenceScreen preference = mDisplayRotationPreference;
        StringBuilder summary = new StringBuilder();
        Boolean rotationEnabled = Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) != 0;
        int mode = Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION_ANGLES,
                DisplayRotation.ROTATION_0_MODE|DisplayRotation.ROTATION_90_MODE|DisplayRotation.ROTATION_270_MODE);

        if (!rotationEnabled) {
            summary.append(getString(R.string.display_rotation_disabled));
        } else {
            ArrayList<String> rotationList = new ArrayList<String>();
            String delim = "";
            summary.append(getString(R.string.display_rotation_enabled) + " ");
            if ((mode & DisplayRotation.ROTATION_0_MODE) != 0) {
                rotationList.add(ROTATION_ANGLE_0);
            }
            if ((mode & DisplayRotation.ROTATION_90_MODE) != 0) {
                rotationList.add(ROTATION_ANGLE_90);
            }
            if ((mode & DisplayRotation.ROTATION_180_MODE) != 0) {
                rotationList.add(ROTATION_ANGLE_180);
            }
            if ((mode & DisplayRotation.ROTATION_270_MODE) != 0) {
                rotationList.add(ROTATION_ANGLE_270);
            }
            for(int i=0;i<rotationList.size();i++) {
                summary.append(delim).append(rotationList.get(i));
                if (rotationList.size() >= 2 && (rotationList.size() - 2) == i) {
                    delim = " " + ROTATION_ANGLE_DELIM_FINAL + " ";
                } else {
                    delim = ROTATION_ANGLE_DELIM + " ";
                }
            }
            summary.append(" " + getString(R.string.display_rotation_unit));
        }
        preference.setSummary(summary);
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            int best = 0;
            for (int i = 0; i < values.length; i++) {
                long timeout = Long.parseLong(values[i].toString());
                if (currentTimeout >= timeout) {
                    best = i;
                }
            }
            summary = preference.getContext().getString(R.string.screen_timeout_summary,
                    entries[best]);
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    int floatToIndex(float val) {
        String[] indices = getResources().getStringArray(R.array.entryvalues_font_size);
        float lastVal = Float.parseFloat(indices[0]);
        for (int i=1; i<indices.length; i++) {
            float thisVal = Float.parseFloat(indices[i]);
            if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                return i-1;
            }
            lastVal = thisVal;
        }
        return indices.length-1;
    }
    
    public void readFontSizePreference(ListPreference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // mark the appropriate item in the preferences list
        int index = floatToIndex(mCurConfig.fontScale);
        pref.setValueIndex(index);

        // report the current size in the summary text
        final Resources res = getResources();
        String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
        pref.setSummary(String.format(res.getString(R.string.summary_font_size),
                fontSizeNames[index]));
    }
    
    private void updateLightPulseDescription() {
        if (Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.NOTIFICATION_LIGHT_PULSE, 0) == 1) {
            mNotificationPulse.setSummary(getString(R.string.notification_light_enabled));
        } else {
            mNotificationPulse.setSummary(getString(R.string.notification_light_disabled));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateDisplayRotationPreferenceDescription();
        updateLightPulseDescription();

        updateState();
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), true,
                mAccelerometerRotationObserver);
    }

    @Override
    public void onPause() {
        super.onPause();

        getContentResolver().unregisterContentObserver(mAccelerometerRotationObserver);
    }

    private void updateState() {
        updateAccelerometerRotationCheckbox();
        readFontSizePreference(mFontSizePref);
    }

    private void updateAccelerometerRotationCheckbox() {
        mAccelerometer.setChecked(Settings.System.getInt(
                getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) != 0);
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }
    
    private void updateHdmiResolutionSummary(Object value){       
        CharSequence[] summaries = getResources().getTextArray(R.array.hdmi_resolution_summaries);
        CharSequence[] values = mHdmiResolution.getEntryValues();
        for (int i=0; i<values.length; i++) {
            if (values[i].equals(value)) {
                mHdmiResolution.setSummary(summaries[i]);
                break;
            }
        }
    }
    
    private void updateAccelerometerCoordinateSummary(Object value){       
        CharSequence[] summaries = getResources().getTextArray(R.array.accelerometer_summaries);
        CharSequence[] values = mAccelerometerCoordinate.getEntryValues();
        for (int i=0; i<values.length; i++) {
            if (values[i].equals(value)) {
                mAccelerometerCoordinate.setSummary(summaries[i]);
                break;
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mAccelerometer) {
            try {
                IWindowManager wm = IWindowManager.Stub.asInterface(
                        ServiceManager.getService(Context.WINDOW_SERVICE));
                if (mAccelerometer.isChecked()) {
                    wm.thawRotation();
                } else {
                    wm.freezeRotation(Surface.ROTATION_0);
                }
            } catch (RemoteException exc) {
                Log.w(TAG, "Unable to save auto-rotate setting");
            }
        } else if (preference == mNotificationPulse) {
            boolean value = mNotificationPulse.isChecked();
            Settings.System.putInt(getContentResolver(), Settings.System.NOTIFICATION_LIGHT_PULSE,
                    value ? 1 : 0);
            return true;
        } else if (preference == mBatteryPulse) {
            boolean value = mBatteryPulse.isChecked();
            Settings.System.putInt(getContentResolver(), Settings.System.BATTERY_LIGHT_PULSE,
                    value ? 1 : 0);
            return true;
        } else if (preference == mHdmiIgnoreGsensor ) {
        	boolean value = mHdmiIgnoreGsensor.isChecked();
        	Settings.System.putInt(getContentResolver(), Settings.System.HDMI_IGNORE_GSENSOR,
        		value ? 1 : 0);
        	return true;
        } else if (preference == mVolumeWake) {
            Settings.System.putInt(getContentResolver(), Settings.System.VOLUME_WAKE_SCREEN,
                    mVolumeWake.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mTrackballWake) {
            Settings.System.putInt(getContentResolver(), Settings.System.LOCKSCREEN_ENABLE_TRACKBALL_KEY,
                    mTrackballWake.isChecked() ? 1 : 0);
            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        }
        if (KEY_HDMI_RESOLUTION.equals(key))
        {
            String value = String.valueOf(objValue);
            try {
                Settings.System.putString(getContentResolver(), 
                        Settings.System.HDMI_RESOLUTION, value);
                updateHdmiResolutionSummary(objValue);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist key hdmi resolution setting", e);
            }
        }
        if (KEY_ACCELEROMETER_COORDINATE.equals(key)) {
        	String value = String.valueOf(objValue);
        	try {
        		Settings.System.putString(getContentResolver(),
        			Settings.System.ACCELEROMETER_COORDINATE, value);
        		updateAccelerometerCoordinateSummary(objValue);
        	} catch (NumberFormatException e) {
        		Log.e(TAG, "could not persist key accelerometer coordinate setting", e);
        	}
        }
        return true;
   } 
}
