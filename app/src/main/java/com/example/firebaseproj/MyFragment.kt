package com.example.firebaseproj

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.bumptech.glide.request.RequestOptions
import com.example.firebaseproj.databinding.DialogImageBinding
import com.example.firebaseproj.databinding.DialogNicknameBinding
import com.example.firebaseproj.databinding.FragmentMyBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.storage.storage
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface ModifyCallback {
    fun nicknameModify(nickname: String)
    fun imageModify(bitmap: Bitmap)
}

class MyFragment : Fragment(), ModifyCallback {
    lateinit var fragmentMyBinding: FragmentMyBinding
    lateinit var reqGallery: ActivityResultLauncher<Intent>
    lateinit var reqCamera: ActivityResultLauncher<Intent>
    lateinit var filePath: String


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentMyBinding = FragmentMyBinding.inflate(layoutInflater, container, false)
        val view = fragmentMyBinding.root
        val auth = Firebase.auth
        val userUid = auth.uid
        galleryLauncher(this)
        cameraLauncher(this)

        getUserInformation(userUid!!)
        logoutClick()

        fragmentMyBinding.tvBtnModify.setOnClickListener {
            nicknameDialog(this)
        }
        fragmentMyBinding.userImage.setOnClickListener {
            imageDialog()
        }

        val credentialManager = CredentialManager.create(requireActivity().applicationContext)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(getString(R.string.web_client_id))
            .build()

        val request: GetCredentialRequest =
            GetCredentialRequest.Builder().addCredentialOption(
                googleIdOption
            ).build()

        fragmentMyBinding.tvBtnExit.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = credentialManager.getCredential(
                        request = request,
                        context = requireContext(),
                    )
                    handleSignIn(result)
                } catch (e: GetCredentialException) {
                    Log.e(ContentValues.TAG, "lifecycleScope error occurred", e)
                }
            }
        }

        return view
    }

    // user 정보를 auth.uid를 통해 가져오는 method
    private fun getUserInformation(userUid: String) {
        val db = Firebase.firestore

        val documentRef = db.collection("userInfoData").document(userUid)
        documentRef.get()
            .addOnSuccessListener {
                if (it.exists()) {
                    val userInfo = it.toObject<UserInfo>()
                    val userImageDownloadURL = userInfo?.userFirstImage
                    val userNickname = userInfo?.userName
                    // uri를 통해 result를 받게되면 역직렬화 과정에서 문제가 발생하므로 toString으로 string값으로 받아 사용하는 것이 편리하다.

                    Glide.with(requireContext())
                        .load(userImageDownloadURL)
                        .apply(RequestOptions().transform(Rotate(90)))
                        .into(fragmentMyBinding.userImage)

                    fragmentMyBinding.tvNickname.text = userNickname
                } else {
                    Log.d("document snapshot doesn't exists", "error")
                }
            }
    }

    // activityresultcontract를 통해 가져온 bitmap을 db와 storage에 업로드하는 callback method
    override fun imageModify(bitmap: Bitmap) {
        val auth = Firebase.auth
        val storage = Firebase.storage
        val db = Firebase.firestore
        val storageRef = storage.reference.child("images/${auth.uid}.jpg")
        val byteOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteOutputStream)

        val data = byteOutputStream.toByteArray()
        val uploadTask = storageRef.putBytes(data)
        uploadTask.addOnCompleteListener {
            if (it.isSuccessful) {
                storageRef.downloadUrl.addOnSuccessListener {
                    db.collection("userInfoData").document(auth.uid!!)
                        .update("userFirstImage", it)
                    getUserInformation(auth.uid!!)
                }
            } else {
                Log.d("error", "${it.exception}")
            }
        }
    }

    // image 교체 dialog 생성 method
    private fun imageDialog() {
        val builder = AlertDialog.Builder(context)
        val dialogInflater = DialogImageBinding.inflate(layoutInflater)
        val dialogView = dialogInflater.root
        val group = dialogInflater.group

        builder.setTitle("이미지 변경")
        builder.setMessage("사진을 가져올 방법을 선택해주세요.")
        builder.setView(dialogView)

        builder.setPositiveButton("확인") { dialog, _ ->
            when (group.checkedRadioButtonId) {
                R.id.radio_camera -> {
                    getUserImageFromCamera()
                }
                R.id.radio_gallery -> {
                    getUserImageFromGallery()
                }
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("취소") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    // todo - 닉네임 변경 method callback 등록 - 완료
    override fun nicknameModify(nickname: String) {
        val auth = Firebase.auth
        val db = Firebase.firestore
        db.collection("userInfoData").document(auth.uid!!)
            .update("userName", nickname)

        getUserInformation(auth.uid!!)
        // todo - dialog 내부 editText에서 첫글자 Capital처리 - custom을 통해 완료
        // todo - 글자 수 0자 이상일 때만 입력이 확인되도록 설정
        // todo - 닉네임 변경 후에 정보를 새로고침 해주기 - 완료
    }

    // nickname dialog 생성 method
    private fun nicknameDialog(callback: ModifyCallback) {
        val builder = AlertDialog.Builder(context)
        val dialogInflater = DialogNicknameBinding.inflate(layoutInflater)
        val dialogView = dialogInflater.root
        val editText = dialogInflater.dialogNicknameEdit

        builder.setTitle("닉네임 변경")
        builder.setMessage("변경할 닉네임을 입력해주세요.")
        builder.setView(dialogView)

        builder.setPositiveButton("확인") { dialog, _ ->
            val inputText = editText.text.toString()
            callback.nicknameModify(inputText)
            dialog.dismiss()
        }

        builder.setNegativeButton("취소") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    // 로그아웃 method
    private fun logoutClick() {
        fragmentMyBinding.tvBtnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.googleLoginFragment, true)
                .build()
            findNavController().navigate(R.id.googleLoginFragment, null, navOptions)
        }
    }

    // 회원탈퇴 로그인 인증을 한번 더 거쳐야 가능한 method
    private fun exitClick(userUid: String) {
        val db = Firebase.firestore
        val storage = Firebase.storage
        val storageRef = storage.reference.child("images/$userUid.jpg")

        val user = FirebaseAuth.getInstance().currentUser
        user?.delete()?.addOnCompleteListener {
            if (it.isSuccessful) {
                // userUid를 통해 db와 storage에 접근하여 해당 uid 관련 정보 전부 삭제
                db.collection("userInfoData").document(userUid).delete()
                storageRef.delete()
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.googleLoginFragment, true)
                    .build()
                findNavController().navigate(R.id.googleLoginFragment, null, navOptions)
            } else {
                Log.d("error occurred", "${it.exception}")
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            // GoogleIdToken credential
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        // Use googleIdTokenCredential and extract id to validate and
                        // authenticate on your server.
                        // todo - googleIdTokenCredential 상수를 통해 사용자의 데이터를 추출한다.
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)
                        firebaseAuthWithGoogleIdToken(googleIdTokenCredential.idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(ContentValues.TAG, "Received an invalid google id token response", e)
                    }
                } else {
                    // Catch any unrecognized custom credential type here.
                    Log.e(ContentValues.TAG, "Unexpected type of credential")
                }
            }
            else -> {
                // Catch any unrecognized credential type here.
                Log.e(ContentValues.TAG, "Unexpected type of credential")
            }
        }
    }

    private fun firebaseAuthWithGoogleIdToken(googleIdToken: String) {
        val auth = Firebase.auth

        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        auth.signInWithCredential(authCredential)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    // firebase auth에서 회원정보 입력 후에 -> uid비교 -> 등록된 회원이면 백스텍 제거 -> 메인프래그먼트 이동
                    val userUid = it.result.user?.uid
                    exitClick(userUid!!)
                }
                else {
                    Log.d("task error occurred", "${it.exception}")
                }
            }
    }

    private fun galleryLauncher(callback: ModifyCallback) {
        reqGallery = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            try {
                val inputStream = context?.contentResolver?.openInputStream(it.data!!.data!!)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                callback.imageModify(bitmap)
                inputStream!!.close()
                // inputStream을 디코딩하여 bitmap을 추출하는 방식으로 glide에 bitmap을 담아준다.
                Glide.with(requireContext())
                    .load(bitmap)
                    .override(150, 150)
                    .into(fragmentMyBinding.userImage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("IntentReset")
    private fun getUserImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        reqGallery.launch(intent)
    }

    private fun cameraLauncher(callback: ModifyCallback) {
        reqCamera = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            try {
                val bitmap = BitmapFactory.decodeFile(filePath)
                callback.imageModify(bitmap)
                // filepath를 디코딩하여 bitmap을 추출하는 방식으로 glide에 bitmap을 담아준다.
                Glide.with(requireContext())
                    .load(bitmap)
                    .apply(RequestOptions().transform(Rotate(90)))
                    .override(150, 150)
                    .into(fragmentMyBinding.userImage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getUserImageFromCamera() {
        val timeStamp: String = SimpleDateFormat("yyMMdd_HHmmss", Locale.KOREA).format(Date())
        val storageDir: File? = context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile(
            "JPEG_${timeStamp}",
            ".jpg",
            storageDir
        )
        filePath = file.absolutePath
        val photoUri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "com.example.firebaseproj.fileprovider", file
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        reqCamera.launch(intent)
    }

}