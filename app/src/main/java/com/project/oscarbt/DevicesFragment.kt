package com.project.oscarbt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.ListFragment
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView

import java.util.ArrayList
import java.util.Collections


class DevicesFragment : ListFragment() {

    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val listItems: ArrayList<BluetoothDevice> = ArrayList()
    private var listAdapter: ArrayAdapter<BluetoothDevice>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        listAdapter = object : ArrayAdapter<BluetoothDevice>(activity!!, 0, listItems) {
            override fun getView(position: Int, view: View?, parent: ViewGroup): View {
                var view: View? = view
                val device = listItems[position]
                if (view == null)
                    view = activity!!.layoutInflater.inflate(R.layout.device_list_item, parent, false)
                val text1 = view!!.findViewById<TextView>(R.id.text1)
                val text2 = view.findViewById<TextView>(R.id.text2)
                text1.text = device.name
                text2.text = device.address
                return view
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setListAdapter(null)
        val header = activity!!.layoutInflater.inflate(R.layout.device_list_header, null, false)
        listView.addHeaderView(header, null, false)
        setEmptyText("initializing...")
        (listView.emptyView as TextView).textSize = 18f
        setListAdapter(listAdapter)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater!!.inflate(R.menu.menu_devices, menu)
        if (!activity!!.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            menu!!.findItem(R.id.bt_settings).isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        if (bluetoothAdapter == null || !activity!!.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            setEmptyText("<bluetooth not supported>")
        else if (!bluetoothAdapter.isEnabled)
            setEmptyText("<bluetooth is disabled>")
        else
            setEmptyText("<no bluetooth devices found>")
        refresh()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item!!.itemId
        if (id == R.id.bt_settings) {
            val intent = Intent()
            intent.action = android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            startActivity(intent)
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    fun refresh() {
        listItems.clear()
        if (bluetoothAdapter != null) {
            for (device in bluetoothAdapter.bondedDevices)
                if (device.type != BluetoothDevice.DEVICE_TYPE_LE)
                    listItems.add(device)
        }
        Collections.sort(listItems) { a, b -> compareTo(a, b) }
        listAdapter?.notifyDataSetChanged()
    }

    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        val device = listItems[position - 1]
        val args = Bundle()
        args.putString("device", device.address)
        val fragment = TerminalFragment()
        fragment.arguments = args
        fragmentManager!!.beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit()
    }

    /**
     * sort by name, then address. sort named devices first
     */
    internal fun compareTo(a: BluetoothDevice, b: BluetoothDevice): Int {
        val aValid = a.name != null && !a.name.isEmpty()
        val bValid = b.name != null && !b.name.isEmpty()
        if (aValid && bValid) {
            val ret = a.name.compareTo(b.name)
            return if (ret != 0) ret else a.address.compareTo(b.address)
        }
        if (aValid) return -1
        return if (bValid) +1 else a.address.compareTo(b.address)
    }


}