package com.hesihai.fuckhwcloudgrow.storage;

import cn.hutool.core.io.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * 原子文本/JSON 文件写入工具。
 *
 * <p>统一“写临时文件 -> 原子替换 -> 收紧权限”的流程，避免登录态、签到结果写到一半时损坏。
 * 原子移动与 POSIX 权限继续使用 NIO：Hutool 的文件移动不保证原子性，直接替换会破坏这里的语义。</p>
 */
public final class AtomicJsonFile {

    private AtomicJsonFile() {
    }

    /**
     * 序列化对象并原子写入目标文件。
     *
     * @param target 目标文件
     * @param value  待序列化对象
     * @param mapper 使用的 ObjectMapper
     * @throws IOException 写入或替换失败
     */
    public static void writeJson(Path target, Object value, ObjectMapper mapper) throws IOException {
        writeText(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    /**
     * 原样写入文本（例如 Playwright storageState），再原子替换目标文件。
     *
     * <p>步骤顺序有讲究：先写同目录临时文件，再 ATOMIC_MOVE 原子替换，
     * 最后收紧权限——保证任何时刻目标文件要么是旧内容要么是完整新内容。</p>
     *
     * @param target  目标文件
     * @param content 文本内容
     * @throws IOException 写入或替换失败
     */
    public static void writeText(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            FileUtil.mkdir(parent.toFile());
            secureDirectory(parent);
        }
        Path temp = siblingTemp(target);
        FileUtil.writeUtf8String(content, temp.toFile());
        moveReplace(temp, target);
        secureFile(target);
    }

    /**
     * 临时文件与目标文件同目录，保证 ATOMIC_MOVE 在多数文件系统上可用。
     *
     * <p>临时文件名固定为“目标名.tmp”，同一目标文件的并发写入会共用同一临时文件，
     * 因此要求单写者：当前调用方（{@code SigninResultStore}、{@code AuthStateStore}）
     * 均在 {@code SigninService} 的锁内执行，此前提由上层保证。</p>
     */
    public static Path siblingTemp(Path path) {
        return path.resolveSibling(path.getFileName() + ".tmp");
    }

    /**
     * 优先原子移动；文件系统不支持时退化为普通替换。
     *
     * @param source 源文件（临时文件）
     * @param target 目标文件
     * @throws IOException 移动失败
     */
    public static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 收紧文件权限为仅属主可读写；非 POSIX 文件系统忽略。
     *
     * @param path 目标文件
     */
    public static void secureFile(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 等非 POSIX 文件系统忽略
        }
    }

    /**
     * 收紧目录权限为仅属主可访问；非 POSIX 文件系统忽略。
     *
     * @param path 目标目录
     */
    public static void secureDirectory(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 等非 POSIX 文件系统忽略
        }
    }
}
