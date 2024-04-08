package com.mongodb.tasktracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mongodb.tasktracker.model.SlotAdapter
import com.mongodb.tasktracker.model.SlotInfo


class InterfaceFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var slotAdapter: SlotAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_interface, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.my_recycler_view1)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val listener = object : SlotAdapter.OnAttendButtonClickListener {
            override fun onAttendClick(slot: SlotInfo, position: Int) {
                (activity as? HomeActivity)?.attendSlot(slot)
            }
        }
        slotAdapter = SlotAdapter(emptyList(), listener)
        recyclerView.adapter = slotAdapter
        updateSlotsData()
    }


    // Phương thức này cho phép cập nhật dữ liệu slots từ bên ngoài
    fun updateSlotsData(slotsData: List<SlotInfo>? = null) {
        val data = slotsData ?: arguments?.getSerializable("slotsData") as? List<SlotInfo> ?: return
        slotAdapter.updateSlots(data)
    }

}

