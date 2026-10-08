package edu.mbu.library.modules;

import edu.mbu.library.assignments.AssignmentService;
import edu.mbu.library.attendance.AttendanceService;
import edu.mbu.library.labs.LabService;
import edu.mbu.library.marks.MarksService;
import edu.mbu.library.storage.FileStorageService;
import edu.mbu.library.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/** Rules for creating and removing subjects, modules and study materials. Failures are IllegalArgumentExceptions with a user-friendly message. */
@Service
public class ModuleService {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9 _-]{2,20}");
    private static final Pattern YOUTUBE_URL =
            Pattern.compile("^https?://(www\\.|m\\.)?(youtube\\.com|youtu\\.be)/\\S+$", Pattern.CASE_INSENSITIVE);

    private final SubjectRepository subjects;
    private final CourseModuleRepository modules;
    private final MaterialRepository materials;
    private final FileStorageService storage;
    private final LabService labs;
    private final AssignmentService assignments;
    private final AttendanceService attendance;
    private final MarksService marks;

    public ModuleService(SubjectRepository subjects, CourseModuleRepository modules,
                         MaterialRepository materials, FileStorageService storage, LabService labs,
                         AssignmentService assignments, AttendanceService attendance, MarksService marks) {
        this.attendance = attendance;
        this.marks = marks;
        this.subjects = subjects;
        this.modules = modules;
        this.materials = materials;
        this.storage = storage;
        this.labs = labs;
        this.assignments = assignments;
    }

    @Transactional
    public Subject createSubject(User faculty, String code, String name, int semester, Integer credits) {
        code = code == null ? "" : code.trim();
        name = name == null ? "" : name.trim();
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Subject code must be 2-20 characters (letters, numbers, space, dash).");
        }
        if (name.length() < 2 || name.length() > 120) {
            throw new IllegalArgumentException("Subject name must be 2-120 characters.");
        }
        if (semester < 1 || semester > 8) {
            throw new IllegalArgumentException("Semester must be between 1 and 8.");
        }
        int creditValue = credits == null ? 3 : credits;
        if (creditValue < 1 || creditValue > 10) {
            throw new IllegalArgumentException("Credits must be between 1 and 10.");
        }
        if (subjects.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("A subject with code '" + code + "' already exists.");
        }
        Subject s = new Subject();
        s.setCode(code);
        s.setName(name);
        s.setSemester(semester);
        s.setCredits(creditValue);
        s.setFaculty(faculty);
        return subjects.save(s);
    }

    @Transactional
    public void deleteSubject(Subject subject) {
        labs.deleteAllForSubject(subject);
        assignments.deleteAllForSubject(subject);
        attendance.deleteAllForSubject(subject);
        marks.deleteAllForSubject(subject);
        for (CourseModule m : modules.findBySubjectOrderByPositionAscIdAsc(subject)) {
            removeModuleContents(m);
            modules.delete(m);
        }
        subjects.delete(subject);
    }

    @Transactional
    public CourseModule addModule(Subject subject, String title) {
        title = title == null ? "" : title.trim();
        if (title.isEmpty() || title.length() > 150) {
            throw new IllegalArgumentException("Module title is required (up to 150 characters).");
        }
        CourseModule m = new CourseModule();
        m.setSubject(subject);
        m.setTitle(title);
        m.setPosition((int) modules.countBySubject(subject) + 1);
        return modules.save(m);
    }

    @Transactional
    public void deleteModule(CourseModule module) {
        removeModuleContents(module);
        modules.delete(module);
    }

    /** Adds a PDF (file upload) or a YouTube link to a module. */
    @Transactional
    public Material addMaterial(CourseModule module, String title, MaterialType type,
                                MultipartFile file, String url) throws IOException {
        title = title == null ? "" : title.trim();
        if (title.isEmpty() || title.length() > 150) {
            throw new IllegalArgumentException("Title is required (up to 150 characters).");
        }
        if (type == null) {
            throw new IllegalArgumentException("Choose PDF or YouTube video.");
        }
        Material mat = new Material();
        mat.setModule(module);
        mat.setTitle(title);
        mat.setType(type);
        if (type == MaterialType.PDF) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Choose a PDF file to upload.");
            }
            mat.setFileName(storage.saveMaterial(file));
        } else {
            url = url == null ? "" : url.trim();
            if (url.length() > 500 || !YOUTUBE_URL.matcher(url).matches()) {
                throw new IllegalArgumentException("Enter a valid YouTube link (youtube.com or youtu.be).");
            }
            mat.setUrl(url);
            if (mat.getYoutubeId() == null) {
                throw new IllegalArgumentException("That YouTube link has no video id. Copy the link of a specific video.");
            }
        }
        return materials.save(mat);
    }

    @Transactional
    public void deleteMaterial(Material material) {
        storage.deleteQuietly(FileStorageService.MATERIALS, material.getFileName());
        materials.delete(material);
    }

    private void removeModuleContents(CourseModule module) {
        List<Material> list = materials.findByModule(module);
        for (Material mat : list) {
            storage.deleteQuietly(FileStorageService.MATERIALS, mat.getFileName());
        }
        materials.deleteAll(list);
    }
}
