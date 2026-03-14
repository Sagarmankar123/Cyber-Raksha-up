package com.avish.sheidhero;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private final Context context;
    private final List<AppInfo> appList;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(AppInfo app);
        void onTrustChanged(AppInfo app, boolean isTrusted);
    }

    public AppAdapter(Context context, List<AppInfo> appList, OnItemClickListener listener) {
        this.context = context;
        this.appList = appList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo info = appList.get(position);

        holder.nameView.setText(info.name);
        holder.packageView.setText(info.packageName);
        
        // AI result styling
        if (info.vtResult != null && !info.vtResult.isEmpty()) {
            holder.vtResultsView.setVisibility(View.VISIBLE);
            holder.vtResultsView.setText(info.vtResult);
            
            if (info.vtResult.contains("Warning") || info.vtResult.contains("Suspicious")) {
                holder.vtResultsView.setTextColor(ContextCompat.getColor(context, R.color.risk_high));
            } else if (info.vtResult.contains("Clean") || info.vtResult.contains("Verified")) {
                holder.vtResultsView.setTextColor(ContextCompat.getColor(context, R.color.risk_safe));
            } else {
                holder.vtResultsView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            }
        } else {
            holder.vtResultsView.setVisibility(View.GONE);
        }

        // Badge and Card styling
        holder.riskBadge.setText(info.riskLevel.replace(" ⚠️", ""));
        
        if (holder.cardRoot instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) holder.cardRoot;
            MaterialCardView badgeCard = holder.itemView.findViewById(R.id.riskBadgeCard);
            
            int colorRes;
            int strokeColorRes;
            
            if (info.riskLevel.contains("HIGH")) {
                colorRes = R.color.risk_high;
                strokeColorRes = R.color.risk_high;
                card.setStrokeColor(ContextCompat.getColor(context, R.color.risk_high));
                card.setStrokeWidth(3); 
            } else if (info.riskLevel.contains("MEDIUM")) {
                colorRes = R.color.risk_medium;
                strokeColorRes = R.color.risk_medium;
                card.setStrokeColor(ContextCompat.getColor(context, R.color.risk_medium));
                card.setStrokeWidth(3);
            } else {
                colorRes = R.color.status_safe;
                strokeColorRes = R.color.divider_color;
                card.setStrokeColor(ContextCompat.getColor(context, R.color.divider_color)); 
                card.setStrokeWidth(1);
            }
            
            if (badgeCard != null) {
                badgeCard.setCardBackgroundColor(ContextCompat.getColor(context, colorRes));
                holder.riskBadge.setTextColor(ContextCompat.getColor(context, R.color.backgroundColor));
            }
        }

        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(info.packageName);
            holder.iconView.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.iconView.setImageResource(R.mipmap.ic_launcher);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(info);
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView nameView, packageView, vtResultsView, riskBadge;
        View cardRoot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.appIcon);
            nameView = itemView.findViewById(R.id.appName);
            packageView = itemView.findViewById(R.id.packageName);
            vtResultsView = itemView.findViewById(R.id.vtResults);
            riskBadge = itemView.findViewById(R.id.riskBadge);
            cardRoot = itemView.findViewById(R.id.cardRoot);
        }
    }
}
