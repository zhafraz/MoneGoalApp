package com.example.monegoal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton

class KodeUndanganFragment : DialogFragment() {

    companion object {
        private const val ARG_CODE = "arg_invite_code"
        private const val ARG_PARENT_EMAIL = "arg_parent_email"

        fun newInstance(code: String, parentEmail: String?): KodeUndanganFragment {
            val f = KodeUndanganFragment()
            val args = Bundle()
            args.putString(ARG_CODE, code)
            args.putString(ARG_PARENT_EMAIL, parentEmail)
            f.arguments = args
            return f
        }
    }

    private var inviteCode: String? = null
    private var parentEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inviteCode = arguments?.getString(ARG_CODE)
        parentEmail = arguments?.getString(ARG_PARENT_EMAIL)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog_Alert)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // inflate your provided layout (the card xml you posted). Put that layout file in res/layout/dialog_invite_code.xml
        val view = inflater.inflate(R.layout.fragment_kode_undangan, container, false)

        val tvInvitationCode = view.findViewById<TextView>(R.id.tvInvitationCode)
        val btnCopyCode = view.findViewById<MaterialButton>(R.id.btnCopyCode)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnClose)

        tvInvitationCode.text = inviteCode ?: ""

        btnCopyCode.setOnClickListener {
            val clip = ClipData.newPlainText("invite_code", inviteCode ?: "")
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            // simple feedback
            android.widget.Toast.makeText(requireContext(), "Kode disalin", android.widget.Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            // setelah menutup, lanjut ke LoginActivity (sesuaikan jika mau ke layar lain)
            dismiss()
            val ctx = requireActivity()
            val intent = Intent(ctx, LoginActivity::class.java)
            // optional: lempar parent email agar login prefill
            if (!parentEmail.isNullOrBlank()) intent.putExtra("emailOrtu", parentEmail)
            ctx.startActivity(intent)
            ctx.finish()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        // set dialog width to match parent - agar tampilan mirip card center
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}