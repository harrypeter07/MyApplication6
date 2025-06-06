package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainPagerAdapter extends FragmentStateAdapter {
    private String filter = "";
    private final Fragment parent;

    public MainPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
        this.parent = fragment;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return SectionFragment.newInstance("images", filter);
            case 1: return SectionFragment.newInstance("files", filter);
            case 2: return SectionFragment.newInstance("zips", filter);
            case 3: return SectionFragment.newInstance("others", filter);
            default: return SectionFragment.newInstance("others", filter);
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }

    public void filter(String query) {
        this.filter = query;
        notifyDataSetChanged();
    }
} 