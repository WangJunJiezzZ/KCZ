package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.databinding.AdapterMobileRowBinding;
import com.fongmi.android.tv.model.MobileHomeViewModel.Row;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.List;
import java.util.function.Consumer;

public class MobileHomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final VodAdapter.OnClickListener listener;
    private final Consumer<Class> more;
    private List<Row> rows = List.of();
    public MobileHomeAdapter(VodAdapter.OnClickListener listener, Consumer<Class> more) {
        this.listener = listener;
        this.more = more;
    }

    public void submit(List<Row> rows) {
        int oldCount = this.rows.size();
        List<Row> previous = this.rows;
        this.rows = rows;
        if (rows.size() >= previous.size() && rows.subList(0, previous.size()).equals(previous)) {
            if (rows.size() > oldCount) notifyItemRangeInserted(oldCount, rows.size() - oldCount);
        } else notifyDataSetChanged();
    }

    @Override public int getItemCount() { return rows.size(); }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Shelf(AdapterMobileRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((Shelf) holder).bind(rows.get(position));
    }

    class Shelf extends RecyclerView.ViewHolder {
        final AdapterMobileRowBinding binding;
        Shelf(AdapterMobileRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.cards.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext(), RecyclerView.HORIZONTAL, false));
        }
        void bind(Row row) {
            binding.title.setText(row.title());
            binding.more.setVisibility(row.type() == null ? View.GONE : View.VISIBLE);
            binding.more.setOnClickListener(v -> more.accept(row.type()));
            int width = ResUtil.dp2px(binding.getRoot().getResources().getConfiguration().screenWidthDp >= 600 ? 148 : 112);
            VodAdapter cards = new VodAdapter(listener, Style.rect(), new int[]{width, width * 3 / 2});
            binding.cards.setAdapter(cards);
            cards.setItems(row.items());
        }
    }
}
