SUBDIRS := install config valid post-install pre-install build commons

.PHONY: all $(SUBDIRS)

all: $(SUBDIRS)

$(SUBDIRS):
	$(MAKE) -C $@ run
