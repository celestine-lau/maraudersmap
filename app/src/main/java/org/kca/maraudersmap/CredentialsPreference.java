/*
    Marauder's Map - Pokemon Go pokescanner
    Copyright (C) 2016  Celestine Lau

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package org.kca.maraudersmap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

/**
 * Created by win7ia on 8/23/2016.
 */
public class CredentialsPreference extends DialogPreference
{
    private static final String TAG = "CredentialsPreference";
    private static final int MAX_CREDENTIALS = 6;
    private static final int[] USERNAME_FIELD_RESIDS = {R.id.username1Value, R.id.username2Value,
        R.id.username3Value, R.id.username4Value, R.id.username5Value, R.id.username6Value};
    private static final int[] PASSWORD_FIELD_RESIDS = {R.id.password1Value, R.id.password2Value,
        R.id.password3Value, R.id.password4Value, R.id.password5Value, R.id.password6Value};
    private EditText[] usernameFields;
    private EditText[] passwordFields;
    private Context mContext;

    public CredentialsPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        mContext = getContext();
        usernameFields = new EditText[MAX_CREDENTIALS];
        passwordFields = new EditText[MAX_CREDENTIALS];
        setDialogLayoutResource(R.layout.credentials_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
    }

    @Override
    public void onBindDialogView(View view)
    {
        super.onBindDialogView(view);
        for (int i = 0; i < MAX_CREDENTIALS; i++)
        {
            usernameFields[i] = (EditText)view.findViewById(USERNAME_FIELD_RESIDS[i]);
            passwordFields[i] = (EditText)view.findViewById(PASSWORD_FIELD_RESIDS[i]);
        }
        SharedPreferences sharedPref = getSharedPreferences();
        String usernames = sharedPref.getString(mContext.getString(R.string.pref_usernames), "username");
        String passwords = sharedPref.getString(mContext.getString(R.string.pref_passwords), "password");
        String[] usernameTokens = usernames.split(" ");
        String[] passwordTokens = passwords.split(" ");
        populateTextFields(usernameTokens, passwordTokens);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult)
    {
        if (positiveResult)
        {
            StringBuffer usernameBuf = new StringBuffer();
            StringBuffer passwordBuf = new StringBuffer();
            for (int i = 0; i < MAX_CREDENTIALS; i++)
            {
                String username = usernameFields[i].getText().toString().trim();
                String password = passwordFields[i].getText().toString().trim();
                if (username.equals("") || password.equals(""))
                {
                    break;
                }
                if (username.contains(" ") || password.contains(" "))
                {
                    new AlertDialog.Builder(mContext)
                            .setMessage(R.string.invalid_credentials_message)
                            .setTitle(R.string.invalid_credentials_title)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setNeutralButton(android.R.string.ok, null)
                            .create()
                            .show();
                    return;
                }
                if (usernameBuf.length() > 0)
                {
                    usernameBuf.append(" ");
                }
                usernameBuf.append(username);
                if (passwordBuf.length() > 0)
                {
                    passwordBuf.append(" ");
                }
                passwordBuf.append(password);
            }
            SharedPreferences sharedPref = getSharedPreferences();
            SharedPreferences.Editor ed = sharedPref.edit();
            ed.putString("usernames", usernameBuf.toString());
            ed.putString("passwords", passwordBuf.toString());
            ed.apply();
            Log.d(TAG, "Committing credentials " + usernameBuf.toString() + " " + passwordBuf.toString());
        }
    }

    private void populateTextFields(String[] usernames, String[] passwords)
    {
        for (int i = 0; i < Math.min(MAX_CREDENTIALS, usernames.length); i++)
        {
            usernameFields[i].setText(usernames[i]);
            passwordFields[i].setText(passwords[i]);
        }
    }

    private String getUsernames()
    {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < MAX_CREDENTIALS; i++)
        {
            if (usernameFields[i].getText().toString().length() == 0)
            {
                break;
            }
            if (sb.length() > 0) sb.append(" ");
            sb.append("\"" + usernameFields[i].getText().toString().trim() + "\"");
        }
        return sb.toString();
    }

    private String getPasswords()
    {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < MAX_CREDENTIALS; i++)
        {
            if (passwordFields[i].getText().toString().length() == 0)
            {
                break;
            }
            if (sb.length() > 0) sb.append(" ");
            sb.append("\"" + passwordFields[i].getText().toString().trim() + "\"");
        }
        return sb.toString();
    }

    @Override
    protected Parcelable onSaveInstanceState()
    {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent())
        {
            return superState;
        }
        final SavedState myState = new SavedState(superState);
        myState.usernames = getUsernames();
        myState.passwords = getPasswords();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state)
    {
        if (state == null || !(state instanceof SavedState))
        {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState)state;
        super.onRestoreInstanceState(myState.getSuperState());
        populateTextFields(myState.usernames.split(" "), myState.passwords.split(" "));
    }

    private static class SavedState extends Preference.BaseSavedState
    {
        private String usernames, passwords;
        public SavedState(Parcelable superState)
        {
            super(superState);
        }

        public SavedState(Parcel source)
        {
            super(source);
            usernames = source.readString();
            passwords = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags)
        {
            super.writeToParcel(dest, flags);
            dest.writeString(usernames);
            dest.writeString(passwords);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>()
        {
            @Override
            public SavedState createFromParcel(Parcel parcel)
            {
                return new SavedState(parcel);
            }

            @Override
            public SavedState[] newArray(int size)
            {
                return new SavedState[size];
            }
        };
    }
}
