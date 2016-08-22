package com.tomeokin.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.tomeokin.phonenumberutil.PhoneNumberUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance(this);
        List<CountryCodeData> countryCodeDatas = new ArrayList<>();
        String[] countries = Locale.getISOCountries();
        String language = Locale.getDefault().getLanguage();
        for (String country : countries) {
            Locale locale = new Locale(language, country);
            int phoneCode = phoneNumberUtil.getCountryCodeForRegion(locale.getCountry());
            countryCodeDatas.add(
                new CountryCodeData(String.valueOf(phoneCode), locale.getDisplayCountry(), locale.getCountry()));
        }
        Collections.sort(countryCodeDatas);

        TextView hello = (TextView) findViewById(R.id.hello);
        for (CountryCodeData data : countryCodeDatas) {
            hello.append(data.formatWithDescription());
            hello.append("\n");
        }
    }
}
