package com.dohealth.handheld.ui.connection

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dohealth.handheld.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : ListAdapter<BluetoothDevice, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding, onDeviceClick)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    fun addDevice(device: BluetoothDevice) {
        val currentList = currentList.toMutableList()
        if (!currentList.any { it.address == device.address }) {
            currentList.add(device)
            submitList(currentList)
        }
    }
    
    fun addDeviceForCf(device: BluetoothDevice) {
        val currentList = currentList.toMutableList()
        if (!currentList.any { it.address == device.address }) {
            // Los dispositivos RFID se agregan al principio de la lista
            currentList.add(0, device)
            submitList(currentList)
        }
    }
    
    fun clearDevices() {
        submitList(emptyList())
    }
    
    fun getDeviceCount(): Int {
        return currentList.size
    }
    
    class DeviceViewHolder(
        private val binding: ItemDeviceBinding,
        private val onDeviceClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(device: BluetoothDevice) {
            binding.deviceNameText.text = device.name ?: "Dispositivo Desconocido"
            binding.deviceAddressText.text = device.address
            
            binding.root.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }
    
    class DeviceDiffCallback : DiffUtil.ItemCallback<BluetoothDevice>() {
        override fun areItemsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean {
            return oldItem.address == newItem.address
        }
        
        override fun areContentsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean {
            return oldItem.name == newItem.name && oldItem.address == newItem.address
        }
    }
}

