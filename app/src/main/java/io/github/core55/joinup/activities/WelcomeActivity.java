/*
  Authors: S. Stefani
 */

package io.github.core55.joinup.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import io.github.core55.joinup.R;
import io.github.core55.joinup.activities.CreateActivity;
import io.github.core55.joinup.activities.LoginActivity;

public class WelcomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        String username = sharedPref.getString(getString(R.string.user_username), "Unknown User");

        if (!username.equals("Unknown User")) {
            Intent i = new Intent(this, CreateActivity.class);
            startActivity(i);
        }

        Button btn_login = (Button) findViewById(R.id.btn_login);
        btn_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createMeetup(v);
            }
        });

        Button btn_login_welcome = (Button) findViewById(R.id.btn_login_welcome);
        btn_login_welcome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginActivity(v);
            }
        });

    }

    protected void createMeetup(View v) {
        Intent i = new Intent(this, CreateActivity.class);
        startActivity(i);
    }

    protected void loginActivity(View v) {
        Intent i = new Intent(this, LoginActivity.class);
        startActivity(i);
    }
}
