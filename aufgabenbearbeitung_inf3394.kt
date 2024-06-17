import java.time.LocalDate
import java.time.temporal.ChronoUnit

//Error wird definiert
val error = Exception("Es gab einen Fehler")

//Enums werden erstellt
enum class Status(var status: String){
    TODO("Noch nicht begonnen"),
    DOING("In Arbeit"),
    DONE("Fertig"),
}

enum class Priority {
    HIGH,
    MEDIUM,
    LOW;

    companion object {
        fun fromFactor(factor: Double): Priority {
            return when(factor.toInt()) {
                1 -> HIGH
                2 -> MEDIUM
                3 -> LOW
                else -> throw IllegalArgumentException("Ungültiger Faktor: $factor")
            }
        }
    }
}

//Interface wird definiert
interface Prioritizable{
    fun prioritize(): Double

}

//Klassen werden definiert
open class WorkUnit(val title: String, val description: String, var deadline: LocalDate, var status: Status) : Prioritizable{
    var priority: Priority? = null
        get() = Priority.fromFactor(this.prioritize())
    fun changeStatus(status: Status){
        this.status = status
    }

    open fun getSummary(): String {
        return " ${this.title} mit der Beschreibung \"$description\" muss bis $deadline " +
                "erledigt sein. Der aktuelle Status ist $status. "
    }

    override fun prioritize(): Double {
        var time_prio = 0.0
        var status_prio = 0.0
        val days_between = ChronoUnit.DAYS.between(LocalDate.now(), deadline).toInt()
        if(days_between <= 7){
            time_prio = 1.0
        }
        else if(days_between <= 31){
            time_prio = 2.0
        }
        else{
            time_prio = 3.0
        }

        if(this.status == Status.DOING){
            status_prio = 1.0
        }
        else if(this.status == Status.TODO){
            status_prio = 2.0
        }
        else{
            status_prio = 3.0
        }

        return (time_prio + status_prio) / 2
    }
}
//Klassen Task und Project werden erstellt
open class Task protected constructor(title: String, description: String, deadline: LocalDate, var steps: MutableList<String>,
           var estimatedTime: Int = 0, status:  Status = Status.TODO) : WorkUnit(title, description, deadline, status),
            Prioritizable{
    override fun getSummary(): String{
        var returnString = "Die Aufgabe" + super.getSummary() + "Die Arbeitsschritte sind: "
        if(this.steps.size > 0) {
            returnString += this.steps[0]
            for (i in 1..<this.steps.size) {
                returnString += ", " + this.steps[i]
            }
        }
        if(this.estimatedTime == 0){
            returnString += ". Die Bearbeitungszeit kann noch nicht abgeschätzt werden."
            return returnString
        }
        returnString += ". Es wird voraussichtlich ${this.estimatedTime} Minuten dauern."
        return returnString
    }

    override fun prioritize(): Double {
        val workunit_average = super<WorkUnit>.prioritize()
        var step_prio = 0.0
        var duration_prio = 0.0

        step_prio = if(this.steps.size > 11){
            1.0
        }
        else if(this.steps.size in 5..10){
            2.0
        }
        else{
            3.0
        }

        duration_prio = if(this.estimatedTime < 60){
            1.0
        }
        else if(this.estimatedTime in 60..180){
            2.0
        }
        else{
            3.0
        }
        val task_average = (step_prio + duration_prio) / 2
        return (task_average + workunit_average) / 2
    }
}

class Project(title: String, description: String, deadline: LocalDate, pTasks: MutableList<Task>,
              status: Status = Status.TODO) : WorkUnit(title, description, deadline, status), Prioritizable{
    val tasks: MutableList<Task> = pTasks

    //In Project wird die berechnete Eigenschaft progress erstellt
    var progress: Double = 0.0
        private set
        get(){
            field = tasks.count{it.status == Status.DONE}.toDouble()
            field /= tasks.size
            field *= 100
            return field
        }

    override fun getSummary(): String{
        return "Das Projekt" + super.getSummary() + "Das Projekt enthält ${this.tasks.size} Aufgaben. Aktuell sind ${"%.2f".format(this.progress)}% abgeschlossen"
    }

    fun addTask(newTask: Task){
        if(newTask.deadline.isAfter(this.deadline)){
            throw Exception("Task Deadline can't be after Project Deadline")
        }
        this.tasks.add(newTask)
    }

    fun checkTasks(){
        tasks.forEach{ task: Task ->
            if(task is SingleTask){
                if(task.reminder == 0){
                    println("Erinnerung für einmalige Aufgabe ${task.title}!")
                }
            }
            else if(task is RecurringTask){
                if(LocalDate.now().isAfter(task.deadline)){
                    if(LocalDate.now().isBefore(task.deadline.plusDays(task.frequency.toLong()))){
                        println("Wiederkehrende Aufgabe ${task.title} ist überfällig und wird neu geplant")
                        task.deadline = task.deadline.plusDays(task.frequency.toLong())
                        println("Die neue Deadline für ${task.title} ist ${task.deadline}")
                    }
                    else{
                        throw error
                    }
                }
            }

        }
    }

    override fun prioritize(): Double {
        var priority_sum = 0.0
        this.tasks.forEach{task ->
            priority_sum += task.prioritize()
        }
        return priority_sum / tasks.size
    }

}

class SingleTask(title: String, description: String, deadline: LocalDate, steps: MutableList<String>, estimatedTime: Int = 0,
                 status:  Status = Status.TODO) : Task(title, description, deadline, steps, estimatedTime, status){
    var reminder: Int = 0
        private set
        get(){
            val remindDate = deadline.minusDays(2)
            if(LocalDate.now().isAfter(remindDate)){
                return 0
            }
            return ChronoUnit.DAYS.between(LocalDate.now(), remindDate).toInt()
        }
}


class RecurringTask(title: String, description: String, deadline: LocalDate, steps: MutableList<String>, estimatedTime: Int = 0,
                    val frequency: Int, status:  Status = Status.TODO) : Task(title, description, deadline, steps, estimatedTime, status)

class Manager(val projects: MutableList<Project>) {
    val todo = mutableListOf<Task>()

    fun getToDoList(): MutableList<Task> {

        this.todo.clear()

        this.projects.forEach{project: Project ->
            project.tasks.forEach {task: Task ->
                if(task.status != Status.DONE){
                    this.todo.add(task)
                }
            }
        }
        return this.todo
    }

    fun getPriotityToDo(): MutableList<Task>{
        val priority_todo = mutableListOf<Task>()
        this.todo.forEach{task ->
            if(task.prioritize() <= 1.5){
                priority_todo.add(task)
            }
        }
        return priority_todo
    }

    fun getAvgTime(): Double{
        var time_sum = 0
        val prio_todo = this.getPriotityToDo()
        prio_todo.forEach{task ->
            time_sum += task.estimatedTime
        }
        return time_sum.toDouble() / prio_todo.size
    }
}

//Tests
fun main(){
    val autokauf = SingleTask("Auto kaufen", "Ein schönes Auto kaufen",
            LocalDate.of(2024, 6, 16), mutableListOf("Marke suchen", "Modell suchen", "Kaufen"),
            10)

    val duschen = RecurringTask("Duschen", "Duschen obwohl ich Informatik studiere",
            LocalDate.of(2024, 6, 1),
            mutableListOf("League of Legends schließen", "Dusche an machen", "Wasserstrahl ausweichen", "Back to League"),
            1, 21)

    val projekt = Project("Projekt", "Ansammlung an Aufgaben", LocalDate.of(2025, 1, 1),
                          mutableListOf(autokauf, duschen))

    val frontend = SingleTask("Frontend", "Ein schönes Frontend bauen", LocalDate.of(2024, 6, 20),
                                mutableListOf("HTML", "CSS", "JS", "divs", "divs", "divs", "divs", "divs", "divs", "divs", "divs", "divs"),
                                30, Status.DOING)

    val backend = SingleTask("Backend", "Ein funktionierendes Backend programmieren",
                            LocalDate.of(2024, 8, 1), mutableListOf("Routing", "POST Requests",
                                    "DB", "Response", "5", "6", "7", "8", "9", "10", "11", "12"), 10, Status.DOING)

    val website = Project("Website programmieren", "Eine schöne Website programmieren",
                            LocalDate.of(2025, 1, 1), mutableListOf(frontend, backend))

    val webdeveloper = Manager(mutableListOf(projekt, website))

    projekt.checkTasks()

    println("${autokauf.title} Priorität: ${autokauf.prioritize()}")
    println("${duschen.title} Priorität: ${duschen.prioritize()}")
    println("${projekt.title} Priorität: ${projekt.prioritize()}")

    println("Allgemeine Todo-Liste: ${webdeveloper.getToDoList().map { it.title }}")
    println("Wichtige Todos: ${webdeveloper.getPriotityToDo().map { it.title }}")
    println("Im Schnitt dauern diese wichtigen Aufgaben jeweils ${webdeveloper.getAvgTime()} Minuten (schön wärs)")
}

