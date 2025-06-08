package com.example.peppergptintegration

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ChildProfilePagerAdapter(
    private val childId: String,
    fragmentActivity: FragmentActivity
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> PerformanceDetailsFragment.newInstance(childId)
            1 -> SessionHistoryFragment.newInstance(childId)
            2 -> ProgressTrendsFragment.newInstance(childId)
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}