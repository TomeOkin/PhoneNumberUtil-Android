/*
 * Copyright 2016 TomeOkin
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tomeokin.sample;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.tomeokin.phonenumberutil.PhoneNumberUtil;

import java.util.Locale;

public class CountryCodeData implements Parcelable, Comparable<CountryCodeData> {
    public final String countryCode;
    public final String country;
    private final String displayString;

    public CountryCodeData(String countryCode, String displayString, String country) {
        this.countryCode = countryCode;
        this.displayString = displayString;
        this.country = country;
    }

    public static CountryCodeData getDefault(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String simCountryIso = telephonyManager.getSimCountryIso();
        if (simCountryIso == null) {
            simCountryIso = telephonyManager.getNetworkCountryIso();
            if (simCountryIso == null) {
                simCountryIso = Locale.getDefault().getCountry();
            }
        }
        simCountryIso = simCountryIso.toUpperCase(Locale.US);
        if (TextUtils.isEmpty(simCountryIso)) {
            simCountryIso = "CN";
        }
        int countryCode = PhoneNumberUtil.getInstance(context).getCountryCodeForRegion(simCountryIso);

        return new CountryCodeData(String.valueOf(countryCode), new Locale("", simCountryIso).getDisplayCountry(),
            simCountryIso);
    }

    public String getCountryCode() {
        return countryCode;
    }

    public final String formatCountryCode() {
        return "+" + this.countryCode;
    }

    public final String formatSimple() {
        return String.format("%s +%s", this.country, this.countryCode);
    }

    public final String formatWithDescription() {
        return String.format("%s (+%s)", this.displayString, this.countryCode);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.countryCode);
        dest.writeString(this.country);
        dest.writeString(this.displayString);
    }

    protected CountryCodeData(Parcel in) {
        this.countryCode = in.readString();
        this.country = in.readString();
        this.displayString = in.readString();
    }

    public static final Creator<CountryCodeData> CREATOR = new Creator<CountryCodeData>() {
        @Override public CountryCodeData createFromParcel(Parcel source) {return new CountryCodeData(source);}

        @Override public CountryCodeData[] newArray(int size) {return new CountryCodeData[size];}
    };

    @Override public int compareTo(@NonNull CountryCodeData another) {
        return this.displayString.compareTo(another.displayString);
    }
}
