package pt.ipb.nmsfs;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;

import fuse.FuseException;
import fuse.FuseFS;
import fuse.FuseFtype;
import fuse.compat.FuseStat;

public class ScalarNode extends FileNode {
	private byte[] content;
	private SnmpBackend backend;

	public ScalarNode(OID oid) {
		this(oid, new byte[0]);
	}

	public ScalarNode(OID oid, byte[] content) {
		super(oid);
		setContent(content);
	}

	public ScalarNode(OID oid, SnmpBackend backend) {
		super(oid);
		this.backend = backend;
		try {
			refreshContent();
		} catch (FuseException e) {
			e.printStackTrace();
		}
	}

	protected FuseStat createStat() {
		FuseStat stat = new FuseStat();

		stat.mode = FuseFtype.TYPE_FILE | 0444;
		stat.uid = stat.gid = 0;
		stat.ctime = stat.mtime = stat.atime = (int) (System.currentTimeMillis() / 1000L);
		stat.size = 0;
		stat.blocks = 0;

		return stat;
	}

	public synchronized void read(ByteBuffer buff, long offset) throws FuseException {
		if (offset >= content.length)
			return;

		int length = buff.capacity();
		if (offset + length > content.length)
			length = content.length - (int) offset;

		buff.put(content, (int) offset, length);
	}

	public void write(ByteBuffer buff, long offset) throws FuseException {
		throw new FuseException("Read Only").initErrno(FuseException.EROFS);
	}

	public void open(int flags) throws FuseException {
		if (flags == FuseFS.O_RDWR || flags == FuseFS.O_WRONLY)
			throw new FuseException("Read Only").initErrno(FuseException.EROFS);

		refreshContent();
	}

	private void refreshContent() throws FuseException {
		VariableBinding varBind;
		try {
			varBind = backend.get(getOid());
			setContent(varBind.getVariable().toString().getBytes());
			if (varBind.isException()) {
				throw new FuseException("IO Error").initErrno(FuseException.EIO);
			}
		} catch (IOException e) {
			throw new FuseException("IO Error").initErrno(FuseException.EIO);
		}
	}

	public void release(int flags) throws FuseException {
		// noop
	}

	public void truncate(long size) throws FuseException {
		throw new FuseException("Read Only").initErrno(FuseException.EROFS);
	}

	public void utime(int atime, int mtime) throws FuseException {
		// noop
	}

	//
	// file content access

	public synchronized byte[] getContent() {
		return content;
	}

	public synchronized void setContent(byte[] content) {
		// stat is by declaration read-only - we must create a copy before
		// modifying it's attributes
		FuseStat stat = (FuseStat) super.getStat().clone();

		if (this.content == null)
			stat.ctime = (int) (System.currentTimeMillis() / 1000L);

		this.content = content;

		stat.mtime = stat.atime = (int) (System.currentTimeMillis() / 1000L);
		stat.size = content.length;
		stat.blocks = (content.length + 511) / 512;

		super.setStat(stat);
	}
}
