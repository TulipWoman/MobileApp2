package com.example.mylocation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TaskListAdapter extends RecyclerView.Adapter<TaskListAdapter.TaskViewHolder> {

    public interface OnTaskClickListener {
        void onTaskClick(StoredLocation loc);
    }

    private final List<StoredLocation> tasks;
    private final OnTaskClickListener  listener;

    public TaskListAdapter(List<StoredLocation> tasks, OnTaskClickListener listener) {
        this.tasks    = tasks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        StoredLocation loc = tasks.get(position);
        holder.bind(loc, listener);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {

        private final TextView titleText;
        private final TextView subtitleText;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText    = itemView.findViewById(R.id.taskItemTitle);
            subtitleText = itemView.findViewById(R.id.taskItemSubtitle);
        }

        void bind(StoredLocation loc, OnTaskClickListener listener) {
            titleText.setText(loc.locationName);

            // Build subtitle: show date/time if set, otherwise description
            StringBuilder sub = new StringBuilder();
            if (loc.dueDate != null && !loc.dueDate.isEmpty()) {
                sub.append(loc.dueDate);
                if (loc.dueTime != null && !loc.dueTime.isEmpty()) {
                    sub.append(" at ").append(loc.dueTime);
                }
            } else if (loc.description != null && !loc.description.isEmpty()) {
                sub.append(loc.description);
            }
            subtitleText.setText(sub.toString());
            subtitleText.setVisibility(sub.length() > 0 ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> listener.onTaskClick(loc));
        }
    }
}