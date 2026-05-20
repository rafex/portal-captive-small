SUBDIRS := install config valid post-install pre-install build commons

.PHONY: all git $(SUBDIRS)

all: $(SUBDIRS)

$(SUBDIRS):
	$(MAKE) -C $@ run

git:
	$(MAKE) -C git run
