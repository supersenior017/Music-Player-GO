package com.iven.musicplayergo.adapters;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.iven.musicplayergo.R;

import java.util.Random;

public class ColorsAdapter extends RecyclerView.Adapter<ColorsAdapter.SimpleViewHolder> {

    //fixed int array of accent colors
    private static final int[] colors = new int[]{
            R.color.red,
            R.color.pink,
            R.color.purple,
            R.color.deep_purple,
            R.color.indigo,
            R.color.blue,
            R.color.light_blue,
            R.color.cyan,
            R.color.teal,
            R.color.green,
            R.color.amber,
            R.color.orange,
            R.color.deep_orange,
            R.color.brown,
            R.color.gray,
            R.color.blue_gray
    };
    private final Activity mActivity;
    private final int mAccent;
    private final AccentChangedListener mOnAccentChangedListener;

    public ColorsAdapter(@NonNull Activity activity, final int accent) {
        mActivity = activity;
        mAccent = accent;
        mOnAccentChangedListener = (AccentChangedListener) mActivity;
    }

    public static int getRandomColor() {
        int rnd = new Random().nextInt(colors.length);
        return colors[rnd];
    }

    @Override
    @NonNull
    public SimpleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        final View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.color_option, parent, false);
        return new SimpleViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull SimpleViewHolder holder, int position) {

        final int color = colors[holder.getAdapterPosition()];
        final int drawable = color != mAccent ? R.drawable.ic_checkbox_blank : R.drawable.ic_checkbox_marked;
        final int parsedColor = ContextCompat.getColor(mActivity, color);
        holder.color.setImageResource(drawable);
        holder.color.setColorFilter(parsedColor);
    }

    @Override
    public int getItemCount() {

        return colors.length;
    }

    public interface AccentChangedListener {
        void onAccentChanged(final int color);
    }

    class SimpleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final ImageView color;

        SimpleViewHolder(@NonNull final View itemView) {
            super(itemView);

            color = (ImageView) itemView;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            //recreate the activity only if necessary
            final int color = colors[getAdapterPosition()];
            if (color != mAccent) {
                mOnAccentChangedListener.onAccentChanged(color);
            }
        }
    }
}