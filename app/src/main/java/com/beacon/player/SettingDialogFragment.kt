package com.beacon.player

import android.util.Log
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.lang.ClassCastException

class SettingDialogFragment: DialogFragment(){
    internal lateinit var listener: NoticeDialogListener

    interface NoticeDialogListener{
        fun onDialogPositiveClick(prefix: String, postfix: String)
        fun onDialogNeutralClick(dialog: DialogFragment)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            listener = context as NoticeDialogListener
        }catch (e: ClassCastException){
            throw ClassCastException((context.toString() +
                    " must implement NoticeDialogListener"))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val dialogView = layoutInflater.inflate(R.layout.dialog_setting, null)
            val args = requireArguments()

            val editTextPrefix: EditText = dialogView.findViewById(R.id.editText_prefix)
            val editTextPostfix: EditText = dialogView.findViewById(R.id.editText_postfix)

            val prefixPrev = if(args.containsKey("MUSIC_TITLE_PREFIX")) {
                args.getString("MUSIC_TITLE_PREFIX")
            }else{
                Log.w("DIALOG", "no saved prefix")
                ""
            }
            val postfixPrev = if(args.containsKey("MUSIC_TITLE_POSTFIX")) {
                args.getString("MUSIC_TITLE_POSTFIX")
            }else{
                Log.w("DIALOG", "no saved postfix")
                ""
            }

            editTextPrefix.hint = "search prefix: $prefixPrev"
            editTextPostfix.hint = "search prefix: $postfixPrev"

            builder.setView(dialogView
            ).setPositiveButton("APPLY") { _, _ ->
                listener.onDialogPositiveClick(editTextPrefix.text.toString(), editTextPostfix.text.toString())
            }.setNegativeButton("DENY") { _, _ ->
            }.setNeutralButton("RESET") { _, _ ->
                listener.onDialogNeutralClick(this)
            }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}