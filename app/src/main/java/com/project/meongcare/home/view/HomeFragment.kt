package com.project.meongcare.home.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.project.meongcare.CalendarBottomSheetFragment
import com.project.meongcare.MainActivity
import com.project.meongcare.R
import com.project.meongcare.databinding.FragmentHomeBinding
import com.project.meongcare.home.viewmodel.HomeViewModel
import com.project.meongcare.login.model.data.local.UserPreferences
import com.project.meongcare.onboarding.model.data.local.DateSubmitListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(), DateSubmitListener, DogProfileClickListener {
    private lateinit var fragmentHomeBinding: FragmentHomeBinding
    private lateinit var mainActivity: MainActivity

    private val homeViewModel: HomeViewModel by viewModels()

    @Inject
    lateinit var userPreferences: UserPreferences

    lateinit var currentAccessToken: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        fragmentHomeBinding = FragmentHomeBinding.inflate(inflater)
        mainActivity = activity as MainActivity

        currentAccessToken = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJpZCI6MSwiZXhwIjoxNzAyOTI0MTE1fQ.Kqpv9fK3LWpVcbA6zOwkhpnAJ637OzzZnQDOzywmFos"
//        getAccessToken()

        homeViewModel.homeProfileResponse.observe(viewLifecycleOwner) { homeProfileResponse ->
            if (homeProfileResponse != null) {
                Glide.with(this)
                    .load(homeProfileResponse.imageUrl)
                    .placeholder(R.drawable.home_profile_default_image)
                    .error(R.drawable.home_profile_default_image)
                    .into(fragmentHomeBinding.imageviewHomeProfile)
            }
        }

        homeViewModel.homeSelectedDate.observe(viewLifecycleOwner) { selectedDate ->
            if (selectedDate != null) {
                Log.d("homeViewmodel-selectedDate", selectedDate)
                // 가로 달력 날짜 selectedDate로 설정

                if (homeViewModel.homeSelectedDogId.value != null) {
                    // 이상증상 목록 조회
                    homeViewModel.getDogSymptom(homeViewModel.homeSelectedDogId.value!!, dateFormatter(homeViewModel.homeSelectedDate.value!!), currentAccessToken)
                }
            }
        }

        homeViewModel.homeDogList.observe(viewLifecycleOwner) { dogList ->
            if (dogList != null) {
                fragmentHomeBinding.recyclerviewHomeDog.visibility = View.VISIBLE
                fragmentHomeBinding.linearlayoutDogExist.visibility = View.VISIBLE
                fragmentHomeBinding.linearlayoutDogNotExist.visibility = View.GONE
                val adapter = fragmentHomeBinding.recyclerviewHomeDog.adapter as HomeDogProfileAdapter
                adapter.updateDogProfileList(dogList)
                if (homeViewModel.homeSelectedDogPos.value == null) {
                    homeViewModel.setSelectedDogPos(0)
                }
            } else {
                fragmentHomeBinding.recyclerviewHomeDog.visibility = View.GONE
                fragmentHomeBinding.linearlayoutDogExist.visibility = View.GONE
                fragmentHomeBinding.linearlayoutDogNotExist.visibility = View.VISIBLE
            }
        }

        homeViewModel.homeSelectedDogId.observe(viewLifecycleOwner) { selectedDogId ->
            if (selectedDogId != null) {
                // 이상증상 목록 조회
                homeViewModel.getDogSymptom(selectedDogId, dateFormatter(homeViewModel.homeSelectedDate.value!!), currentAccessToken)
            }
        }

        homeViewModel.homeSelectedDogPos.observe(viewLifecycleOwner) { selectedDogPos ->
            if (selectedDogPos != null) {
                Log.d("homeSelectedDogName", homeViewModel.homeDogList.value!![selectedDogPos].name)
                homeViewModel.setSelectedDogId(homeViewModel.homeDogList.value!![selectedDogPos].dogId)
                val adapter = fragmentHomeBinding.recyclerviewHomeDog.adapter as HomeDogProfileAdapter
                adapter.updateSelectedPos(selectedDogPos)
            }
        }

        homeViewModel.homeDogSymptom.observe(viewLifecycleOwner) { dogSymptom ->
            if (dogSymptom.symptoms.isEmpty()) {
                fragmentHomeBinding.textviewHomeSymptom2.setText(R.string.home_symptom_not_exist)
                fragmentHomeBinding.recyclerviewHomeSymptom.visibility = View.GONE
            } else {
                fragmentHomeBinding.textviewHomeSymptom2.setText(R.string.home_symptom_exist)
                fragmentHomeBinding.recyclerviewHomeSymptom.visibility = View.VISIBLE
                dogSymptom.symptoms.forEach {
                    Log.d("homeDogSymptom", it)
                }
                val adapter = fragmentHomeBinding.recyclerviewHomeSymptom.adapter as HomeSymptomAdapter
                adapter.updateSymptomList(dogSymptom.symptoms)
            }
        }

        fragmentHomeBinding.run {
//            homeViewModel.getUserProfile(currentAccessToken)
            homeViewModel.getUserProfile(currentAccessToken)
            homeViewModel.getDogList(currentAccessToken)

            imageviewHomeCalendar.setOnClickListener {
                val modalBottomSheet = CalendarBottomSheetFragment()
                modalBottomSheet.setDateSubmitListener(this@HomeFragment)
                modalBottomSheet.setStyle(DialogFragment.STYLE_NORMAL, R.style.RoundCornerCalendarDialogTheme)
                modalBottomSheet.show(mainActivity.supportFragmentManager, modalBottomSheet.tag)
            }

            imageviewHomeAlert.setOnClickListener {
                // 알림 화면으로 전환
            }

            imageviewHomeAddDog.setOnClickListener {
                // 강아지 정보 등록 화면으로 전환
            }

            recyclerviewHomeDog.run {
                adapter = HomeDogProfileAdapter(layoutInflater, context, this@HomeFragment)
                layoutManager = LinearLayoutManager(mainActivity, LinearLayoutManager.HORIZONTAL, false)
            }

            recyclerviewHomeSymptom.run {
                adapter = HomeSymptomAdapter(layoutInflater, context)
                layoutManager = LinearLayoutManager(mainActivity, LinearLayoutManager.HORIZONTAL, false)
            }
        }

        return fragmentHomeBinding.root
    }

    private fun getAccessToken() {
        runBlocking {
            userPreferences.accessToken.collect { accessToken ->
                if (accessToken != null) {
                    currentAccessToken = accessToken
                }
            }
        }
    }

    override fun onDateSubmit(str: String) {
        homeViewModel.setSelectedDate(str)
    }

    fun getCurrentDate(): String {
        val currentDate = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return currentDate.format(formatter)
    }

    fun dateFormatter(date: String): String {
        val currentDate = getCurrentDate()
        if (date == currentDate) {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            return LocalDate.now().atStartOfDay().format(formatter)
        } else {
            return (date + "T23:59:59")
        }
    }

    override fun onDogProfileClick(pos: Int) {
        homeViewModel.setSelectedDogPos(pos)
    }
}
