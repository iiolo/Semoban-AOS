package com.project.meongcare.login.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.NidOAuthLogin
import com.navercorp.nid.oauth.OAuthLoginCallback
import com.navercorp.nid.profile.NidProfileCallback
import com.navercorp.nid.profile.data.NidProfileResponse
import com.project.meongcare.BuildConfig
import com.project.meongcare.R
import com.project.meongcare.databinding.FragmentLoginBinding
import com.project.meongcare.login.model.data.repository.FirebaseCloudMessagingService
import com.project.meongcare.login.model.entities.LoginRequest
import com.project.meongcare.login.viewmodel.LoginViewModel
import com.project.meongcare.medicalRecord.viewmodel.UserViewModel
import com.project.meongcare.snackbar.view.CustomSnackBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class LoginFragment : Fragment() {
    private lateinit var binding: FragmentLoginBinding
    private lateinit var provider: String

    private val googleSignInClient: GoogleSignInClient by lazy { getGoogleClient() }
    val googleAuthLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            getGoogleResult(task)
        }
    private val loginViewModel: LoginViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()
    private val myFirebaseMessagingService = FirebaseCloudMessagingService()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentLoginBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        getProvider()
        addListeners()
        loginResponseProcess()
    }

    private fun loginResponseProcess() {
        loginViewModel.loginResponse.observe(viewLifecycleOwner) { loginResponse ->
            if (loginResponse == null) {
                Log.e("LoginFragment", "서버 응답 없음 (네트워크 오류)")
                CustomSnackBar.make(
                    requireView(),
                    R.drawable.snackbar_error_16dp,
                    getString(R.string.snack_bar_failure),
                ).show()
                return@observe
            }
            when (loginResponse.code()) {
                200 -> {
                    if (loginResponse.body() != null) {
                        Log.e("isFirstLogin", loginResponse.body()?.isFirstLogin.toString())
                        userViewModel.setAccessToken(loginResponse.body()?.accessToken)
                        userViewModel.setRefreshToken(loginResponse.body()?.refreshToken)
                        userViewModel.setIsFirstLogin(loginResponse.body()?.isFirstLogin)
                        when (loginResponse.body()?.isFirstLogin) {
                            true -> {
                                val bundle = Bundle()
                                bundle.putBoolean("isFirstRegister", true)
                                findNavController().navigate(R.id.action_loginFragment_to_dogAddOnBoardingFragment, bundle)
                            }
                            false -> findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                            else -> {
                                Log.e("LoginFragment", "isFirstLogin이 null")
                                CustomSnackBar.make(
                                    requireView(),
                                    R.drawable.snackbar_error_16dp,
                                    getString(R.string.snack_bar_failure),
                                ).show()
                            }
                        }
                    }
                }
                400 -> {
                    CustomSnackBar.make(
                        requireView(),
                        R.drawable.snackbar_error_16dp,
                        getString(R.string.snack_bar_login_failure_deleted),
                    ).show()
                    when (provider) {
                        "kakao" -> deleteKakaoAccount()
                        "naver" -> deleteNaverAccount()
                        "google" -> deleteGoogleAccount()
                    }
                }
                else -> {
                    Log.e("LoginFragment", "로그인 실패: ${loginResponse.code()}")
                    CustomSnackBar.make(
                        requireView(),
                        R.drawable.snackbar_error_16dp,
                        getString(R.string.snack_bar_login_failure),
                    ).show()
                }
            }
        }
    }

    private fun getProvider() {
        userViewModel.providerPreferencesLiveData.observe(viewLifecycleOwner) { provider ->
            if (provider != null) {
                this.provider = provider
            }
        }
    }

    private fun addListeners() {
        binding.buttonKakaoLogin.setOnClickListener {
            kakaoLogin()
        }

        binding.buttonNaverLogin.setOnClickListener {
            naverLogin()
        }

        binding.buttonGoogleLogin.setOnClickListener {
            googleLogin()
        }
    }

    private fun kakaoLogin() {
        // 카카오계정으로 로그인 공통 callback 구성
        // 카카오톡으로 로그인 할 수 없어 카카오계정으로 로그인할 경우 사용됨
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                Log.e("Login-kakao", "카카오계정으로 로그인 실패", error)
            } else if (token != null) {
                Log.d("Login-kakao", "카카오계정으로 로그인 성공")
                getKakaoLoginInfo()
            }
        }

        // 카카오톡이 설치되어 있으면 카카오톡으로 로그인, 아니면 카카오계정으로 로그인
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(requireContext())) {
            UserApiClient.instance.loginWithKakaoTalk(requireContext()) { token, error ->
                if (error != null) {
                    Log.e("Login-kakao", "카카오톡으로 로그인 실패", error)

                    // 사용자가 카카오톡 설치 후 디바이스 권한 요청 화면에서 로그인을 취소한 경우,
                    // 의도적인 로그인 취소로 보고 카카오계정으로 로그인 시도 없이 로그인 취소로 처리 (예: 뒤로 가기)
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        return@loginWithKakaoTalk
                    }

                    // 카카오톡에 연결된 카카오계정이 없는 경우, 카카오계정으로 로그인 시도
                    UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = callback)
                } else if (token != null) {
                    Log.i("Login-kakao", "카카오톡으로 로그인 성공")
                    getKakaoLoginInfo()
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = callback)
        }
    }

    fun getKakaoLoginInfo() {
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Log.e("Login-kakao", "사용자 정보 요청 실패", error)
            } else if (user != null) {
                Log.d("Login-kakao", "사용자 정보 요청 성공")

                val deviceToken = getDeviceToken()

                // data store에 저장
                userViewModel.setEmail(user.kakaoAccount?.email!!)
                userViewModel.setProvider("kakao")

                val loginRequest =
                    LoginRequest(
                        "${user.id}",
                        "kakao",
                        "${user.kakaoAccount?.email}",
                        "${user.kakaoAccount?.profile?.thumbnailImageUrl}",
                        deviceToken,
                    )
                loginViewModel.postLoginInfo(loginRequest)
            }
        }
    }

    private fun naverLogin() {
        val nidProfileCallback =
            object : NidProfileCallback<NidProfileResponse> {
                override fun onError(
                    errorCode: Int,
                    message: String,
                ) {
                    onFailure(errorCode, message)
                }

                override fun onFailure(
                    httpStatus: Int,
                    message: String,
                ) {
                    val errorCode = NaverIdLoginSDK.getLastErrorCode().code
                    val errorDescription = NaverIdLoginSDK.getLastErrorDescription()
                    Log.d("Login-naver", "errorCode:$errorCode errorDescription:$errorDescription")
                }

                override fun onSuccess(result: NidProfileResponse) {
                    if (result.profile != null) {
                        Log.d("Login-naver", "프로필 가져오기 성공 ${result.profile?.profileImage}")

                        val deviceToken = getDeviceToken()

                        // data store에 저장
                        userViewModel.setEmail(result.profile?.email!!)
                        userViewModel.setProvider("naver")

                        // 서버에 로그인 정보 전송
                        val loginRequest =
                            LoginRequest(
                                "${result.profile?.id}",
                                "naver",
                                "${result.profile?.email}",
                                "${result.profile?.profileImage}",
                                deviceToken,
                            )
                        loginViewModel.postLoginInfo(loginRequest)
                    }
                }
            }

        val oauthLoginCallback =
            object : OAuthLoginCallback {
                override fun onError(
                    errorCode: Int,
                    message: String,
                ) {
                    onFailure(errorCode, message)
                }

                override fun onFailure(
                    httpStatus: Int,
                    message: String,
                ) {
                    val errorCode = NaverIdLoginSDK.getLastErrorCode().code
                    val errorDescription = NaverIdLoginSDK.getLastErrorDescription()
                    Log.e("Login-naver", "errorCode:$errorCode errorDescription:$errorDescription")
                }

                override fun onSuccess() {
                    Log.d("Login-naver", "로그인 성공")
                    NidOAuthLogin().callProfileApi(nidProfileCallback)
                }
            }
        NaverIdLoginSDK.authenticate(requireContext(), oauthLoginCallback)
    }

    private fun googleLogin() {
        val signInIntent = googleSignInClient.signInIntent
        googleAuthLauncher.launch(signInIntent)
    }

    private fun getGoogleClient(): GoogleSignInClient {
        val googleSignInOptions =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestIdToken(BuildConfig.GOOGLE_CLIENT_ID)
                .requestServerAuthCode(BuildConfig.GOOGLE_CLIENT_ID)
                .build()

        return GoogleSignIn.getClient(requireActivity(), googleSignInOptions)
    }

    private fun getGoogleResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            val deviceToken = getDeviceToken()

            // data store에 저장
            userViewModel.setEmail(account.email!!)
            userViewModel.setProvider("google")

            val loginRequest =
                LoginRequest(
                    "${account.idToken}",
                    "google",
                    "${account.email}",
                    "${account.photoUrl}",
                    deviceToken,
                )
            loginViewModel.postLoginInfo(loginRequest)
        } catch (e: ApiException) {
            Log.e("Login-google", e.stackTraceToString())
        }
    }

    fun getDeviceToken(): String {
        val deviceToken =
            runBlocking {
                myFirebaseMessagingService.getToken()
            }
        Log.d("in-getDeviceToken-method", deviceToken)
        return deviceToken
    }

    private fun deleteKakaoAccount() {
        UserApiClient.instance.unlink { error ->
            if (error != null) {
                Log.e("Delete-kakao", "연결 끊기 실패", error)
            } else {
                Log.d("Delete-kakao", "연결 끊기 성공. SDK에서 토큰 삭제 됨")
            }
        }
    }

    private fun deleteNaverAccount() {
        NidOAuthLogin().callDeleteTokenApi(
            object : OAuthLoginCallback {
                override fun onError(
                    errorCode: Int,
                    message: String,
                ) {
                    onFailure(errorCode, message)
                }

                override fun onFailure(
                    httpStatus: Int,
                    message: String,
                ) {
                    Log.e("Delete-naver", "토큰 삭제 실패 : ${NaverIdLoginSDK.getLastErrorDescription()}")
                }

                override fun onSuccess() {
                    Log.d("Delete-naver", "토큰 삭제 성공, 연동 해제 됨")
                }
            },
        )
    }

    private fun deleteGoogleAccount() {
        val gso =
            GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN,
            ).build()
        val googleSignInClient =
            this.let {
                GoogleSignIn.getClient(requireContext(), gso)
            }

        googleSignInClient.revokeAccess()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Delete-google", "회원 탈퇴 성공")
                } else {
                    Log.e("Delete-google", "회원 탈퇴 실패 ${task.result}")
                }
            }
    }
}
