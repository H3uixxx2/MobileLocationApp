package com.mongodb.tasktracker

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mongodb.tasktracker.databinding.ActivityHomeBinding
import com.mongodb.tasktracker.model.CourseInfo
import com.mongodb.tasktracker.model.SlotInfo
import com.mongodb.tasktracker.model.TermInfo
import io.realm.Realm
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import org.bson.Document
import org.bson.types.ObjectId
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.jar.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.location.Location
import androidx.appcompat.app.AlertDialog





class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var app: App

    private var studentName: String? = null
    private var studentEmail: String? = null
    private var departmentName: String? = null
    private var studentId: ObjectId? = null


    private var coursesInfo: List<CourseInfo>? = null
    private var slotsData: List<SlotInfo>? = null
    private var courseTitlesMap = mutableMapOf<String, String>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi tạo Realm
        Realm.init(this)
        val appConfiguration = AppConfiguration.Builder("finalproject-rujev").build()
        app = App(appConfiguration)  // Khởi tạo app

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Nhận email từ Intent và thực hiện truy vấn dữ liệu
        intent.getStringExtra("USER_EMAIL")?.let {
            fetchStudentData(it)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.userInterface -> replaceFragment(InterfaceFragment())
                R.id.user -> replaceFragment(InforFragment())
                R.id.gear -> replaceFragment(GearFragment())
                R.id.shop -> replaceFragment(ShopFragment())
                else -> false
            }
            true
        }

        if (intent.getBooleanExtra("SHOW_INFOR_FRAGMENT", false)) {
            replaceFragment(InforFragment())
        } else {
            replaceFragment(InterfaceFragment())
        }
    }

    //fetch Student from data
    private fun fetchStudentData(userEmail: String) {
        val mongoClient = app.currentUser()?.getMongoClient("mongodb-atlas")
        val database = mongoClient?.getDatabase("finalProject")
        Log.d("checked", "Attempting to fetch student data for email: $userEmail")

        database?.getCollection("Students")?.findOne(Document("email", userEmail))?.getAsync { task ->

            if (task.isSuccess) {
                val studentDocument = task.get()
                Log.d("checked", "Successfully fetched student data: ${studentDocument?.toJson()}")

                // Cập nhật thông tin sinh viên
                studentName = studentDocument?.getString("name")
                this.studentEmail = studentDocument?.getString("email")
                val departmentId = studentDocument?.getObjectId("departmentId")
                this.studentId = studentDocument?.getObjectId("_id")

                if (departmentId != null) {
                    Log.d("checked", "Fetching department data for ID: $departmentId")
                    fetchDepartmentData(departmentId)
                    // Sau khi lấy được departmentId, cần lấy termId tương ứng với department này
                    fetchCurrentTerm(departmentId) { termInfo ->
                        if (termInfo != null) {
                            // Lấy danh sách khóa học mà sinh viên đã đăng ký
                            val enrolledCourses = studentDocument.getList("enrolledCourses", ObjectId::class.java)
                            if (!enrolledCourses.isNullOrEmpty()) {
                                Log.d("checked2", "Fetching courses data for enrolled courses: $enrolledCourses")
                                fetchCoursesData(enrolledCourses)
                                // Bây giờ ta sẽ lấy thông tin về các slot và term dựa trên danh sách khóa học và termId đã lấy được
                                fetchCoursesAndSlots(enrolledCourses, termInfo.termId) // Cần định nghĩa fetchCoursesAndSlots để nhận termId
                            } else {
                                Log.e("checked", "No enrolled courses found for user: $userEmail")
                            }
                        } else {
                            Log.e("checked1", "Unable to find current term for departmentId: $departmentId")
                        }
                    }
                } else {
                    Log.e("checked", "Department ID not found for user: $userEmail")
                }
            } else {
                Log.e("checked", "Error fetching student data: ${task.error}")
            }
        }
    }

    //fetch Department from data
    private fun fetchDepartmentData(departmentId: ObjectId) {
        val mongoClient = app.currentUser()!!.getMongoClient("mongodb-atlas")
        val database = mongoClient.getDatabase("finalProject")
        val departmentsCollection = database.getCollection("Departments")

        val query = Document("_id", departmentId)
        departmentsCollection.findOne(query).getAsync { task ->
            if (task.isSuccess) {
                val departmentDocument = task.get()
                if (departmentDocument != null) {
                    departmentName = departmentDocument.getString("name")
                } else {
                    Log.e("HomeActivity", "Không tìm thấy phòng ban với ID: $departmentId")
                }
            } else {
                Log.e("HomeActivity", "Lỗi khi truy vấn phòng ban: ${task.error}")
            }
        }
    }

    //fetch Courses from data
    private fun fetchCoursesData(courseIds: List<ObjectId>) {
        val mongoClient = app.currentUser()?.getMongoClient("mongodb-atlas")
        val database = mongoClient?.getDatabase("finalProject")
        val coursesCollection = database?.getCollection("Courses")
        val departmentsCollection = database?.getCollection("Departments")

        val coursesInfoTemp = mutableListOf<CourseInfo>()

        courseIds.forEach { courseId ->
            coursesCollection?.findOne(Document("_id", courseId))?.getAsync { task ->
                if (task.isSuccess) {
                    val courseDocument = task.get()
                    val title = courseDocument?.getString("title") ?: "Unknown"
                    val description = courseDocument?.getString("description") ?: "No description"
                    val departmentId = courseDocument?.getObjectId("departmentId")
                    val credits = courseDocument?.getInteger("credits", 0) ?: 0

                    courseTitlesMap[courseId.toString()] = title

                    if (departmentId != null) {
                        departmentsCollection?.findOne(Document("_id", departmentId))?.getAsync { deptTask ->
                            if (deptTask.isSuccess) {
                                val departmentDocument = deptTask.get()
                                val departmentName = departmentDocument?.getString("name") ?: "Unknown department"

                                // Tạo và thêm đối tượng CourseInfo vào danh sách tạm thời
                                coursesInfoTemp.add(CourseInfo(title, description, departmentName, credits))

                                // Kiểm tra xem đã lấy đủ thông tin cho tất cả các khóa học hay chưa
                                if (coursesInfoTemp.size == courseIds.size) {
                                    coursesInfo = coursesInfoTemp
                                    // Cập nhật UI hoặc thực hiện các bước tiếp theo tại đây nếu cần
                                }
                            } else {
                                Log.e("fetchCoursesData", "Error fetching department data: ${deptTask.error}")
                            }
                        }
                    } else {
                        Log.e("fetchCoursesData", "Department ID not found for course: $title")
                    }
                } else {
                    Log.e("fetchCoursesData", "Error fetching course data: ${task.error}")
                }
            }
        }
    }

    private fun checkAndPassCourses(coursesInfo: List<CourseInfo>, totalCourses: Int) {
        if (coursesInfo.size == totalCourses) {
            passCoursesToFragment(coursesInfo)
        }
    }

    private fun passCoursesToFragment(coursesInfo: List<CourseInfo>) {
        this.coursesInfo = coursesInfo
        // Cập nhật InforFragment với dữ liệu mới
        val inforFragment = supportFragmentManager.findFragmentByTag("InforFragment") as? InforFragment
        inforFragment?.let {
            it.arguments = Bundle().apply {
                putString("name", studentName ?: "N/A")
                putString("email", studentEmail ?: "N/A")
                putString("department", departmentName ?: "N/A")
                putSerializable("courses", ArrayList(coursesInfo))
            }
            replaceFragment(it)
        }
    }

    private fun fetchCurrentTerm(departmentId: ObjectId, completion: (TermInfo?) -> Unit) {
        Log.d("CheckTerm", "Starting to fetch current term with departmentId: $departmentId")
        val mongoClient = app.currentUser()?.getMongoClient("mongodb-atlas")
        val database = mongoClient?.getDatabase("finalProject")
        val termsCollection = database?.getCollection("Terms")

        val currentDate = Date()
        val query = Document("\$and", listOf(
            Document("startDate", Document("\$lte", currentDate)),
            Document("endDate", Document("\$gte", currentDate)),
            Document("departments", departmentId)
        ))

        Log.d("CheckTerm", "Query for current term: $query")

        termsCollection?.findOne(query)?.getAsync { task ->
            if (task.isSuccess) {
                val termDocument = task.get()
                Log.d("CheckTerm", "Current term found: ${termDocument?.toJson()}")
                val termInfo = termDocument?.let { doc ->
                    TermInfo(
                        termId = doc.getObjectId("_id"),
                        name = doc.getString("name"),
                        startDate = doc.getDate("startDate").time,
                        endDate = doc.getDate("endDate").time,
                        departments = doc.getList("departments", ObjectId::class.java)
                    )
                }
                completion(termInfo)
            } else {
                Log.e("CheckTerm", "Error fetching current term: ${task.error}")
                completion(null)
            }
        }
    }

    private fun fetchCoursesAndSlots(courseIds: List<ObjectId>, termId: ObjectId) {
        val mongoClient = app.currentUser()?.getMongoClient("mongodb-atlas")
        val database = mongoClient?.getDatabase("finalProject")

        Log.d("fetchCoursesAndSlots", "Start fetching slots with termId: $termId and courseIds: $courseIds")
        val slotsCollection = database?.getCollection("Slots")

        Log.d("fetchCoursesAndSlots", "Fetching slots for termId: $termId and courseIds: $courseIds")

        // Truy vấn Slots dựa vào courseId và termId
        val query = Document("\$and", listOf(
            Document("courseId", Document("\$in", courseIds)),
            Document("termId", termId)
        ))

        slotsCollection?.find(query)?.iterator()?.getAsync { task ->
            if (task.isSuccess) {
                val documents = task.get() // Lấy kết quả truy vấn
                val slots = mutableListOf<SlotInfo>() // Khởi tạo danh sách slots

                documents.forEach { document ->
                    // Xử lý từng document ở đây
                    val slotId = document.getObjectId("_id").toString()
                    val courseId = document.getObjectId("courseId").toString()

                    Log.d("SlotDetail", "Processing slot $slotId for courseId $courseId")

                    // Kiểm tra xem courseId có tồn tại trong courseTitlesMap không
                    if (courseTitlesMap.containsKey(courseId)) {
                        val courseTitle = courseTitlesMap[courseId]
                        // Log title tìm được từ map
                        Log.d("SlotDetail", "Found title for courseId $courseId: $courseTitle")
                    } else {
                        // Log trường hợp không tìm thấy title cho courseId trong map
                        Log.d("SlotDetail", "No title found for courseId $courseId in courseTitlesMap")
                    }

                    val slotInfo = SlotInfo(
                        slotId = slotId,
                        startTime = document.getString("startTime"),
                        endTime = document.getString("endTime"),
                        day = document.getString("day"),
                        courseId = courseId,
                        courseTitle = courseTitlesMap[courseId] ?: "Title not found", // Cập nhật courseTitle từ map
                        building = "Pending" // Sẽ cập nhật sau khi lấy thông tin tòa nhà
                    )
                    slots.add(slotInfo) // Thêm slotInfo vào danh sách
                }

                Log.d("fetchCoursesAndSlots1", "Fetched slots: ${slots.size}")

                // Tiếp tục với việc lấy thông tin tòa nhà
                fetchAttendanceRecordsForSlots(slots, termId) { slotsWithAttendance ->
                    // Bước 3: Thêm thông tin tòa nhà cho từng slot đã có thông tin điểm danh
                    fetchBuildingForSlots(slotsWithAttendance, termId) { fullyUpdatedSlots ->
                        slotsData = fullyUpdatedSlots
                        runOnUiThread {
                            // Gửi dữ liệu đã cập nhật đến UI
                            sendSlotsDataToInterfaceFragment(fullyUpdatedSlots)
                        }
                    }
                }
            } else {
                Log.e("fetchCoursesAndSlots", "Error fetching slots: ${task.error}")
            }
        }
    }

    private fun fetchBuildingForSlots(slots: List<SlotInfo>, termId: ObjectId, completion: (List<SlotInfo>) -> Unit) {
        val mongoClient = app.currentUser()?.getMongoClient("mongodb-atlas")
        val database = mongoClient?.getDatabase("finalProject")
        val roomsCollection = database?.getCollection("Rooms")

        val updatedSlots = mutableListOf<SlotInfo>()
        var callbackCount = 0

        slots.forEach { slot ->
            // Bây giờ truy vấn sẽ bao gồm cả slotId và termId
            val query = Document("\$and", listOf(
                Document("availability", Document("\$elemMatch", Document("slotId", Document("\$oid", slot.slotId)).append("termId", termId)))
            ))

            roomsCollection?.findOne(query)?.getAsync { task ->
                if (task.isSuccess) {
                    val roomDocument = task.get()
                    val building = roomDocument?.getString("building") ?: "Unknown"
                    updatedSlots.add(slot.copy(building = building))
                    Log.d("Debugdepartment", "Building found for SlotID: ${slot.slotId} and TermID: $termId is $building")
                } else {
                    updatedSlots.add(slot)
                    Log.e("Debugdepartment", "Error fetching room data for SlotID: ${slot.slotId} and TermID: $termId: ${task.error}")
                }
                callbackCount++
                if (callbackCount == slots.size) {
                    completion(updatedSlots)
                }
            }
        }
    }

    private fun fetchAttendanceRecordsForSlots(slots: List<SlotInfo>, termId: ObjectId, completion: (List<SlotInfo>) -> Unit) {
        // Đảm bảo có studentId để truy vấn
        val studentId = this.studentId
        if (studentId == null) {
            Log.e("AttendanceError", "Student ID is null")
            return // Dừng xử lý nếu studentId là null
        }

        val attendanceCollection = app.currentUser()?.getMongoClient("mongodb-atlas")?.getDatabase("finalProject")?.getCollection("Attendance")
        val slotsWithAttendance = mutableListOf<SlotInfo>()
        val specifiedDate = Date() // Sử dụng ngày hiện tại

        slots.forEach { slot ->
            val query = Document("\$and", listOf(
                Document("studentId", studentId),
                Document("courseId", Document("\$oid", slot.courseId)),
                Document("termId", termId),
                Document("slotId", Document("\$oid", slot.slotId))
            ))
            Log.d("AttendanceQuery", "Querying attendance for SlotID: ${slot.slotId}, CourseID: ${slot.courseId}, StudentID: $studentId, TermID: $termId")

            attendanceCollection?.findOne(query)?.getAsync { task ->
                if (task.isSuccess) {
                    val attendanceRecord = task.get()

                    Log.d("AttendanceResult", "Attendance record found - SlotID: ${slot.slotId}, CourseID: ${slot.courseId}, StudentID: $studentId, Status: ${attendanceRecord?.getString("status")}, Date: ${attendanceRecord?.getDate("date")}")

                    val attendanceDate = attendanceRecord?.getDate("date")
                    if (attendanceDate != null && isSameDay(attendanceDate, specifiedDate) && canAttend(slot.startTime)) {
                        slot.attendanceDate = SimpleDateFormat("dd/MM/yyyy").format(attendanceDate)
                        slot.attendanceStatus = attendanceRecord.getString("status")
                        slotsWithAttendance.add(slot)
                        Log.d("AttendanceUpdate", "Slot ${slot.slotId} is eligible for attendance today.")
                    }
                } else {
                    Log.e("AttendanceError", "Error fetching attendance record: ${task.error}")
                }

                if (slotsWithAttendance.size == slots.size || slots.indexOf(slot) == slots.lastIndex) {
                    Log.d("AttendanceCompletion", "Completed attendance check for all slots.")
                    completion(slotsWithAttendance)
                }
            }
        }
    }

    fun isSameDay(date1: Date?, date2: Date?): Boolean {
        if (date1 == null || date2 == null) return false

        val fmt = SimpleDateFormat("yyyyMMdd")
        return fmt.format(date1) == fmt.format(date2)
    }

    fun canAttend(startTime: String): Boolean {
        // Định dạng và múi giờ
        val currentZone = TimeZone.getTimeZone("GMT+7")
        val format = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = currentZone
        }

        // Lấy ngày hiện tại
        val specifiedDate = Date()
        val now = Calendar.getInstance(currentZone).apply {
            time = specifiedDate
        }

        // Phân tích giờ bắt đầu từ chuỗi startTime
        val startTimeParsed = format.parse(startTime) ?: return false
        val calendarStartTime = Calendar.getInstance(currentZone).apply {
            time = startTimeParsed
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
        }

        // set the time for attendance
        val calendarStart = calendarStartTime.clone() as Calendar
        calendarStart.add(Calendar.MINUTE, -30) // open early 30 minute

        val calendarEnd = calendarStartTime.clone() as Calendar
        calendarEnd.add(Calendar.MINUTE, 30) // close after 30 minute

        // Kiểm tra xem thời gian hiện tại có nằm trong khoảng thời gian cho phép không
        val canAttend = now.after(calendarStart) && now.before(calendarEnd)
        Log.d("canAttend", "Current time is within the attendance window: $canAttend (Start: ${format.format(calendarStart.time)}, End: ${format.format(calendarEnd.time)})")

        return canAttend
    }

    fun attendSlot(slotInfo: SlotInfo) {
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                confirmAttendance(slotInfo, it)
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to get location.", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmAttendance(slotInfo: SlotInfo, location: Location) {
        val distanceInMeters = FloatArray(2)
        val schoolLatitude = 37.422052
        val schoolLongitude = -122.084150

        Location.distanceBetween(location.latitude, location.longitude, schoolLatitude, schoolLongitude, distanceInMeters)

        if (distanceInMeters[0] <= ALLOWED_DISTANCE_METERS) {
            // Hiển thị AlertDialog để xác nhận
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Xác nhận điểm danh")
            builder.setMessage("Bạn có chắc chắn là đang ở khu vực điểm danh?")
            builder.setPositiveButton("Attend") { dialog, which ->
                updateAttendanceRecord(slotInfo, "present")
                showResultDialog("Đã điểm danh thành công, vui lòng chạy lại ứng dụng")
            }
            builder.setNegativeButton("Cancel", { dialog, which -> dialog.dismiss() })
            builder.show()
        } else {
            showResultDialog("Bạn không ở trong khu vực cho phép.")
        }
    }

    private fun showResultDialog(message: String) {
        AlertDialog.Builder(this).apply {
            setTitle("Kết quả điểm danh")
            setMessage(message)
            setPositiveButton("Ok") { dialog, which -> dialog.dismiss() }
            show()
        }
    }

    private fun updateAttendanceRecord(slotInfo: SlotInfo, status: String) {
        val attendanceCollection = app.currentUser()?.getMongoClient("mongodb-atlas")?.getDatabase("finalProject")?.getCollection("Attendance")
        // Cập nhật trạng thái điểm danh trong collection `Attendance`
        val query = Document("slotId", ObjectId(slotInfo.slotId)).append("studentId", ObjectId(studentId.toString()))
        val update = Document("\$set", Document("status", status))

        attendanceCollection?.updateOne(query, update)?.getAsync { result ->
            if (result.isSuccess) {
                Log.d("updateAttendance", "Attendance status updated successfully.")
                Toast.makeText(this, "Attendance marked as $status.", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("updateAttendance", "Failed to update attendance status.")
                Toast.makeText(this, "Failed to mark attendance.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val ALLOWED_DISTANCE_METERS = 100f // Phạm vi cho phép là 100 mét
    }

    private fun sendSlotsDataToInterfaceFragment(slots: List<SlotInfo>? = null) {
        val dataToPass = slots ?: slotsData
        if (dataToPass != null) {
            Log.d("HomeActivity", "Gửi dữ liệu Slots đến InterfaceFragment, số lượng: ${dataToPass.size}")
            val interfaceFragment = InterfaceFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("slotsData", ArrayList(dataToPass))
                }
            }
            replaceFragment(interfaceFragment)
        } else {
            Log.e("HomeActivity", "Không có dữ liệu Slots để gửi đến InterfaceFragment.")
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        // Kiểm tra và cập nhật dữ liệu cho InterfaceFragment hoặc InforFragment nếu cần
        if (fragment is InterfaceFragment && slotsData != null) {
            fragment.arguments = Bundle().apply {
                putSerializable("slotsData", ArrayList(slotsData))
            }
        } else if (fragment is InforFragment && coursesInfo != null) {
            fragment.arguments = Bundle().apply {
                putString("name", studentName ?: "N/A")
                putString("email", studentEmail ?: "N/A")
                putString("department", departmentName ?: "N/A")
                putSerializable("courses", ArrayList(coursesInfo))
            }
        }

        // Thực hiện thay thế Fragment
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.frame_layout, fragment, fragment.javaClass.simpleName)
            commit()
        }
    }
}
