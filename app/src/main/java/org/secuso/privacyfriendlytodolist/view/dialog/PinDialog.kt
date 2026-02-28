/*
Privacy Friendly To-Do List
Copyright (C) 2016-2025  Simon Breitfelder

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package org.secuso.privacyfriendlytodolist.view.dialog

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.secuso.privacyfriendlytodolist.R
import org.secuso.privacyfriendlytodolist.util.PreferenceMgr

interface PinCallback {
    fun accepted()
    fun declined()
    fun resetApp()
}

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
class PinDialog(context: Context, private val allowReset: Boolean) :
    FullScreenDialog<PinCallback>(context, R.layout.pin_dialog) {
    private var wrongCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textEditPin: EditText = findViewById(R.id.et_pin_pin)
        val buttonOkay: Button = findViewById(R.id.bt_pin_ok)

        // Request focus for first input field.
        textEditPin.requestFocus()
        // Show soft-keyboard
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        checkAndApplyLockout(prefs, buttonOkay)

        buttonOkay.setOnClickListener {
            val pinExpected = prefs.getString(PreferenceMgr.P_PIN.name, "")
            val pinEntered = PreferenceMgr.hashPin(textEditPin.text.toString())
            if (pinExpected == pinEntered) {
                prefs.edit {
                    remove(PreferenceMgr.P_PIN_FAIL_COUNT.name)
                    remove(PreferenceMgr.P_PIN_LOCKOUT_UNTIL.name)
                }
                getDialogCallback().accepted()
                setOnDismissListener(null)
                dismiss()
            } else {
                val failCount = (prefs.getString(PreferenceMgr.P_PIN_FAIL_COUNT.name, "0")?.toIntOrNull() ?: 0) + 1
                val lockoutSeconds = when {
                    failCount >= 10 -> 300L
                    failCount >= 5  -> 30L
                    else -> 0L
                }
                prefs.edit {
                    putString(PreferenceMgr.P_PIN_FAIL_COUNT.name, failCount.toString())
                    if (lockoutSeconds > 0) {
                        putString(PreferenceMgr.P_PIN_LOCKOUT_UNTIL.name,
                            (System.currentTimeMillis() + lockoutSeconds * 1000).toString())
                    }
                }
                wrongCounter++
                Toast.makeText(context, context.resources.getString(R.string.wrong_pin),
                    Toast.LENGTH_SHORT).show()
                textEditPin.setText("")
                textEditPin.isActivated = true
                if (wrongCounter >= 3 && allowReset) {
                    val buttonResetApp: Button = findViewById(R.id.bt_reset_application)
                    buttonResetApp.visibility = View.VISIBLE
                }
                if (lockoutSeconds > 0) checkAndApplyLockout(prefs, buttonOkay)
            }
        }
        val buttonResetApp: Button = findViewById(R.id.bt_reset_application)
        buttonResetApp.setOnClickListener {
            val resetDialogListener = DialogInterface.OnClickListener { dialog, which ->
                if (which == BUTTON_POSITIVE) {
                    getDialogCallback().resetApp()
                }
            }
            val resources = context.resources
            MaterialAlertDialogBuilder(context).apply {
                setMessage(resources.getString(R.string.reset_application_msg))
                setPositiveButton(resources.getString(R.string.yes), resetDialogListener)
                setNegativeButton(resources.getString(R.string.no), resetDialogListener)
                show()
            }
        }
        val buttonNoDeadline: Button = findViewById(R.id.bt_pin_cancel)
        buttonNoDeadline.setOnClickListener {
            getDialogCallback().declined()
            dismiss()
        }
        setOnDismissListener { getDialogCallback().declined() }
        textEditPin.isActivated = true
    }

    private fun checkAndApplyLockout(prefs: SharedPreferences, okButton: Button) {
        val lockoutUntil = prefs.getString(PreferenceMgr.P_PIN_LOCKOUT_UNTIL.name, "0")?.toLongOrNull() ?: 0L
        val remaining = lockoutUntil - System.currentTimeMillis()
        if (remaining > 0) {
            okButton.isEnabled = false
            object : CountDownTimer(remaining, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    okButton.text = context.getString(R.string.pin_locked_countdown, millisUntilFinished / 1000)
                }
                override fun onFinish() {
                    okButton.isEnabled = true
                    okButton.setText(R.string.ok)
                }
            }.start()
        }
    }
}
