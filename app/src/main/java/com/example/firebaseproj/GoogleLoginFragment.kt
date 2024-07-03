package com.example.firebaseproj

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavAction
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.example.firebaseproj.databinding.FragmentGoogleLoginBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class GoogleLoginFragment : Fragment() {
    lateinit var fragmentGoogleLoginBinding: FragmentGoogleLoginBinding
    lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentGoogleLoginBinding = FragmentGoogleLoginBinding.inflate(layoutInflater, container, false)
        val view = fragmentGoogleLoginBinding.root

        return view
    }

    override fun onResume() {
        super.onResume()

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

        fragmentGoogleLoginBinding.googleLoginBtn.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = credentialManager.getCredential(
                        request = request,
                        context = requireContext(),
                    )
                    handleSignIn(result)
                } catch (e: GetCredentialException) {
                    Log.e(TAG, "lifecycleScope error occurred", e)
                }
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
                        Log.e(TAG, "Received an invalid google id token response", e)
                    }
                } else {
                    // Catch any unrecognized custom credential type here.
                    Log.e(TAG, "Unexpected custom type of credential")
                }
            }
            else -> {
                // Catch any unrecognized credential type here.
                Log.e(TAG, "Unexpected type of credential")
            }
        }
    }

    private fun firebaseAuthWithGoogleIdToken(googleIdToken: String) {
        auth = Firebase.auth

        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        auth.signInWithCredential(authCredential)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    // firebase auth에서 회원정보 입력 후에 -> uid비교 -> 등록된 회원이면 백스텍 제거 -> 메인프래그먼트 이동
                    val userUid = it.result.user?.uid
                    checkUserInformation(userUid!!)
                }
                else {
                    Log.d("task error occurred", "${it.exception}")
                }
            }
    }

    private fun checkUserInformation(userUid: String) {
        // firestore DB에서 userInfoData -> uid로 구성된 document가 존재하지 않으면 UserInfoFragment
        // 존재한다면 MainFragment로
        val db = Firebase.firestore

        db.collection("userInfoData").document(userUid)
            .get()
            .addOnSuccessListener {
                if (it.exists()) {
                    findNavController().popBackStack()
                    findNavController().navigate(R.id.myFragment)

                } else {
                    findNavController().popBackStack()
                    findNavController().navigate(R.id.userInfoFragment)
                }
            }
            .addOnFailureListener {
                Log.d("document snapshot catch failed", "$it")
            }
    }

}