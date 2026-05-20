SUBDIRS := install config valid post-install pre-install build commons git

.PHONY: all $(SUBDIRS)

all: $(SUBDIRS)

$(SUBDIRS):
	$(MAKE) -C $@ run
